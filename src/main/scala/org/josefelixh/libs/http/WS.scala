package org.josefelixh.libs.http

import java.io.File
import scala.concurrent.{ Future, Promise }
import play.api.libs.iteratee._
import play.api.libs.iteratee.Input._
import com.ning.http.client.{
AsyncHttpClient,
AsyncHttpClientConfig,
RequestBuilderBase,
FluentCaseInsensitiveStringsMap,
HttpResponseBodyPart,
HttpResponseHeaders,
HttpResponseStatus,
Response => AHCResponse,
PerRequestConfig
}
import collection.immutable.TreeMap
//import play.core.utils.CaseInsensitiveOrdered
import com.ning.http.util.AsyncHttpProviderUtils

object WS {

  import com.ning.http.client.Realm.{ AuthScheme, RealmBuilder }
  import javax.net.ssl.SSLContext

  private var clientHolder: Option[AsyncHttpClient] = None

  def resetClient(): Unit = {
    clientHolder.map { clientRef =>
      clientRef.close()
    }
    clientHolder = None
  }

  def client =
    clientHolder.getOrElse {
      val asyncHttpConfig = new AsyncHttpClientConfig.Builder()
        .setConnectionTimeoutInMs((120000L).toInt)
        .setRequestTimeoutInMs((120000L).toInt)
        .setFollowRedirects((true))
        .setUseProxyProperties((true))
        .setSSLContext(SSLContext.getDefault)
        .setAllowPoolingConnection(true)

      val innerClient = new AsyncHttpClient(asyncHttpConfig.build())
      clientHolder = Some(innerClient)
      innerClient
    }

  def url(url: String): WSRequestHolder = WSRequestHolder(url, Map(), Map(), None, None, None, None, None)

  class WSRequest(_method: String, _auth: Option[Tuple3[String, String, AuthScheme]], _calc: Option[SignatureCalculator]) extends RequestBuilderBase[WSRequest](classOf[WSRequest], _method, false) {

    import scala.collection.JavaConverters._

    def getStringData = body.getOrElse("")
    protected var body: Option[String] = None
    override def setBody(s: String) = { this.body = Some(s); super.setBody(s) }

    protected var calculator: Option[SignatureCalculator] = _calc

    protected var headers: Map[String, Seq[String]] = Map()

    protected var _url: String = null

    //this will do a java mutable set hence the {} response
    _auth.map(data => auth(data._1, data._2, data._3)).getOrElse({})

    private def auth(username: String, password: String, scheme: AuthScheme = AuthScheme.BASIC): WSRequest = {
      this.setRealm((new RealmBuilder())
        .setScheme(scheme)
        .setPrincipal(username)
        .setPassword(password)
        .setUsePreemptiveAuth(true)
        .build())
    }

    def allHeaders: Map[String, Seq[String]] = {
      mapAsScalaMapConverter(request.asInstanceOf[com.ning.http.client.Request].getHeaders()).asScala.map(e => e._1 -> e._2.asScala.toSeq).toMap
    }

    def queryString: Map[String, Seq[String]] = {
      mapAsScalaMapConverter(request.asInstanceOf[com.ning.http.client.Request].getParams()).asScala.map(e => e._1 -> e._2.asScala.toSeq).toMap
    }

    def header(name: String): Option[String] = headers.get(name).flatMap(_.headOption)

    def method: String = _method

    def url: String = _url

    private def ningHeadersToMap(headers: java.util.Map[String, java.util.Collection[String]]) =
      mapAsScalaMapConverter(headers).asScala.map(e => e._1 -> e._2.asScala.toSeq).toMap

    private def ningHeadersToMap(headers: FluentCaseInsensitiveStringsMap) = {
      val res = mapAsScalaMapConverter(headers).asScala.map(e => e._1 -> e._2.asScala.toSeq).toMap
      //todo: wrap the case insensitive ning map instead of creating a new one (unless perhaps immutabilty is important)
      TreeMap(res.toSeq: _*)(new Ordering[String] {
        def compare(x: String, y: String): Int = x.compareToIgnoreCase(y)
      })
    }
    private[libs] def execute: Future[Response] = {
      import com.ning.http.client.AsyncCompletionHandler
      val result = Promise[Response]()
      calculator.map(_.sign(this))
      WS.client.executeRequest(this.build(), new AsyncCompletionHandler[AHCResponse]() {
        override def onCompleted(response: AHCResponse) = {
          result.success(Response(response))
          response
        }
        override def onThrowable(t: Throwable) = {
          result.failure(t)
        }
      })
      result.future
    }

    override def setHeader(name: String, value: String) = {
      headers = headers + (name -> List(value))
      super.setHeader(name, value)
    }

    override def addHeader(name: String, value: String) = {
      headers = headers + (name -> (headers.get(name).getOrElse(List()) :+ value))
      super.addHeader(name, value)
    }

    override def setHeaders(hdrs: FluentCaseInsensitiveStringsMap) = {
      headers = ningHeadersToMap(hdrs)
      super.setHeaders(hdrs)
    }

    override def setHeaders(hdrs: java.util.Map[String, java.util.Collection[String]]) = {
      headers = ningHeadersToMap(hdrs)
      super.setHeaders(hdrs)
    }

    def setHeaders(hdrs: Map[String, Seq[String]]) = {
      headers = hdrs
      hdrs.foreach(header => header._2.foreach(value =>
        super.addHeader(header._1, value)
      ))
      this
    }

    def setQueryString(queryString: Map[String, Seq[String]]) = {
      for ((key, values) <- queryString; value <- values) {
        this.addQueryParameter(key, value)
      }
      this
    }

    override def setUrl(url: String) = {
      _url = url
      super.setUrl(url)
    }

    private[libs] def executeStream[A](consumer: ResponseHeaders => Iteratee[Array[Byte], A]): Future[Iteratee[Array[Byte], A]] = {
      import com.ning.http.client.AsyncHandler
      var doneOrError = false
      calculator.map(_.sign(this))

      var statusCode = 0
      val iterateeP = Promise[Iteratee[Array[Byte], A]]()
      var iteratee: Iteratee[Array[Byte], A] = null

      WS.client.executeRequest(this.build(), new AsyncHandler[Unit]() {
        import com.ning.http.client.AsyncHandler.STATE

        override def onStatusReceived(status: HttpResponseStatus) = {
          statusCode = status.getStatusCode()
          STATE.CONTINUE
        }

        override def onHeadersReceived(h: HttpResponseHeaders) = {
          val headers = h.getHeaders()
          iteratee = consumer(ResponseHeaders(statusCode, ningHeadersToMap(headers)))
          STATE.CONTINUE
        }

        override def onBodyPartReceived(bodyPart: HttpResponseBodyPart) = {
          if (!doneOrError) {
            iteratee = iteratee.pureFlatFold {
              case Step.Done(a, e) => {
                doneOrError = true
                val it = Done(a, e)
                iterateeP.success(it)
                it
              }

              case Step.Cont(k) => {
                k(El(bodyPart.getBodyPartBytes()))
              }

              case Step.Error(e, input) => {
                doneOrError = true
                val it = Error(e, input)
                iterateeP.success(it)
                it
              }
            }
            STATE.CONTINUE
          } else {
            iteratee = null
            // Must close underlying connection, otherwise async http client will drain the stream
            bodyPart.closeUnderlyingConnection()
            STATE.ABORT
          }
        }

        override def onCompleted() = {
          Option(iteratee).map(iterateeP.success(_))
        }

        override def onThrowable(t: Throwable) = {
          iterateeP.failure(t)
        }
      })
      iterateeP.future
    }

  }

  case class WSRequestHolder(url: String,
                             headers: Map[String, Seq[String]],
                             queryString: Map[String, Seq[String]],
                             calc: Option[SignatureCalculator],
                             auth: Option[Tuple3[String, String, AuthScheme]],
                             followRedirects: Option[Boolean],
                             timeout: Option[Int],
                             virtualHost: Option[String]) {

    def sign(calc: SignatureCalculator): WSRequestHolder = this.copy(calc = Some(calc))

    def withAuth(username: String, password: String, scheme: AuthScheme): WSRequestHolder =
      this.copy(auth = Some((username, password, scheme)))

    def withHeaders(hdrs: (String, String)*): WSRequestHolder = {
      val headers = hdrs.foldLeft(this.headers)((m, hdr) =>
        if (m.contains(hdr._1)) m.updated(hdr._1, m(hdr._1) :+ hdr._2)
        else (m + (hdr._1 -> Seq(hdr._2)))
      )
      this.copy(headers = headers)
    }

    def withQueryString(parameters: (String, String)*): WSRequestHolder =
      this.copy(queryString = parameters.foldLeft(queryString) {
        case (m, (k, v)) => m + (k -> (v +: m.get(k).getOrElse(Nil)))
      })

    def withFollowRedirects(follow: Boolean): WSRequestHolder =
      this.copy(followRedirects = Some(follow))

    def withTimeout(timeout: Int): WSRequestHolder =
      this.copy(timeout = Some(timeout))

    def withVirtualHost(vh: String): WSRequestHolder = {
      this.copy(virtualHost = Some(vh))
    }

    def get(): Future[Response] = prepare("GET").execute

    def get[A](consumer: ResponseHeaders => Iteratee[Array[Byte], A]): Future[Iteratee[Array[Byte], A]] =
      prepare("GET").executeStream(consumer)

//    def post[T](body: T)(implicit wrt: Writeable[T], ct: ContentTypeOf[T]): Future[Response] = prepare("POST", body).execute
    def post(body: String): Future[Response] = prepare("POST", body).execute

    def post(body: File): Future[Response] = prepare("POST", body).execute

//    def postAndRetrieveStream[A, T](body: T)(consumer: ResponseHeaders => Iteratee[Array[Byte], A])(implicit wrt: Writeable[T], ct: ContentTypeOf[T]): Future[Iteratee[Array[Byte], A]] = prepare("POST", body).executeStream(consumer)
    def postAndRetrieveStream[A](body: String)(consumer: ResponseHeaders => Iteratee[Array[Byte], A]): Future[Iteratee[Array[Byte], A]] = prepare("POST", body).executeStream(consumer)

//    def put[T](body: T)(implicit wrt: Writeable[T], ct: ContentTypeOf[T]): Future[Response] = prepare("PUT", body).execute
    def put(body: String): Future[Response] = prepare("PUT", body).execute

    def put(body: File): Future[Response] = prepare("PUT", body).execute

//    def putAndRetrieveStream[A, T](body: T)(consumer: ResponseHeaders => Iteratee[Array[Byte], A])(implicit wrt: Writeable[T], ct: ContentTypeOf[T]): Future[Iteratee[Array[Byte], A]] = prepare("PUT", body).executeStream(consumer)
    def putAndRetrieveStream[A](body: String)(consumer: ResponseHeaders => Iteratee[Array[Byte], A]): Future[Iteratee[Array[Byte], A]] = prepare("PUT", body).executeStream(consumer)

    def delete(): Future[Response] = prepare("DELETE").execute

    def head(): Future[Response] = prepare("HEAD").execute

    def options(): Future[Response] = prepare("OPTIONS").execute

    def execute(method: String): Future[Response] = prepare(method).execute

    private[josefelixh] def prepare(method: String) = {
      val request = new WSRequest(method, auth, calc).setUrl(url)
        .setHeaders(headers)
        .setQueryString(queryString)
      followRedirects.map(request.setFollowRedirects(_))
      timeout.map { t: Int =>
        val config = new PerRequestConfig()
        config.setRequestTimeoutInMs(t)
        request.setPerRequestConfig(config)
      }
      virtualHost.map { v =>
        request.setVirtualHost(v)
      }
      request
    }

    private[josefelixh] def prepare(method: String, body: File) = {
      import com.ning.http.client.generators.FileBodyGenerator
      import java.nio.ByteBuffer

      val bodyGenerator = new FileBodyGenerator(body);

      val request = new WSRequest(method, auth, calc).setUrl(url)
        .setHeaders(headers)
        .setQueryString(queryString)
        .setBody(bodyGenerator)
      followRedirects.map(request.setFollowRedirects(_))
      timeout.map { t: Int =>
        val config = new PerRequestConfig()
        config.setRequestTimeoutInMs(t)
        request.setPerRequestConfig(config)
      }
      virtualHost.map { v =>
        request.setVirtualHost(v)
      }

      request
    }

//    private[josefelixh] def prepare[T](method: String, body: T)(implicit wrt: Writeable[T], ct: ContentTypeOf[T]) = {
    private[josefelixh] def prepare(method: String, body: String)(implicit codec: Codec) = {
      val request = new WSRequest(method, auth, calc).setUrl(url)
//        .setHeaders(Map("Content-Type" -> Seq(ct.mimeType.getOrElse("text/plain"))) ++ headers)
//        .setHeaders(Map("Content-Type" -> Seq("application/json")) ++ headers)
          .setHeaders(headers)
        .setQueryString(queryString)
        .setBody(codec.encode(body))
      followRedirects.map(request.setFollowRedirects(_))
      timeout.map { t: Int =>
        val config = new PerRequestConfig()
        config.setRequestTimeoutInMs(t)
        request.setPerRequestConfig(config)
      }
      virtualHost.map { v =>
        request.setVirtualHost(v)
      }
      request
    }
  }
}

case class Response(ahcResponse: AHCResponse) {

  import scala.xml._
  import play.api.libs.json._

  def getAHCResponse = ahcResponse

  def status: Int = ahcResponse.getStatusCode()

  def statusText: String = ahcResponse.getStatusText()

  def header(key: String): Option[String] = Option(ahcResponse.getHeader(key))

  lazy val body: String = {
    // RFC-2616#3.7.1 states that any text/* mime type should default to ISO-8859-1 charset if not
    // explicitly set, while Plays default encoding is UTF-8.  So, use UTF-8 if charset is not explicitly
    // set and content type is not text/*, otherwise default to ISO-8859-1
    val contentType = Option(ahcResponse.getContentType).getOrElse("application/octet-stream")
    val charset = Option(AsyncHttpProviderUtils.parseCharset(contentType)).getOrElse {
      if (contentType.startsWith("text/"))
        AsyncHttpProviderUtils.DEFAULT_CHARSET
      else
        "utf-8"
    }
    ahcResponse.getResponseBody(charset)
  }

  lazy val xml: Elem = XML.loadString(body)

  lazy val json: JsValue = Json.parse(ahcResponse.getResponseBodyAsBytes)

}

case class ResponseHeaders(status: Int, headers: Map[String, Seq[String]])

trait SignatureCalculator {

  def sign(request: WS.WSRequest)

}
