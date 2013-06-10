package org.josefelixh.couch

import org.josefelixh.libs.http._
import com.ning.http.client.Realm.AuthScheme._
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._

case class CouchConfig(
  couchInstanceUrl: String,
  dbName: String,
  credentials: Option[(String,String)] = None
)

object Couch {
  def apply(couchConfig: CouchConfig): Couch = new Couch {
    override val config: CouchConfig = couchConfig
  }
}
trait Couch {
  val config: CouchConfig
  private lazy val couchUrl = config.couchInstanceUrl
  private lazy val dbName = config.dbName
  private lazy val credentials = config.credentials

  private[couch] def couch(path: String): WSRequestHolder = {
    val ws = WS.url(s"$couchUrl$path")
      .withHeaders(
        "Accept" -> "application/json",
        "Content-Type" -> "application/json"
      )

    credentials.map { case (username: String, password: String) =>
      ws.withAuth(username, password, BASIC)
    } getOrElse ws

  }


  private[couch] val db: String => WSRequestHolder = { path => couch(s"/$dbName$path") }

  def info = db("/").get()
  def create = db("/").put("{}")
  def delete = db("/").delete()
  def documents = db("/_all_docs").get()
  def databases = couch("/_all_dbs").get()

  def create[T](docs: Seq[CouchDocument[T]])(implicit fmt: Format[T], exec: ExecutionContext): Future[Seq[CouchDocument[T]]] =
    db("/").post("{}") map { response =>
      (response.json \\ "") map { json =>
        CouchDocument.apply[T](Json.fromJson[T](json).get)(this, implicitly[Format[T]], implicitly[ExecutionContext])
//          .withId((response.json \\ "_id").)
//          .withRev()
      }
    }

}
