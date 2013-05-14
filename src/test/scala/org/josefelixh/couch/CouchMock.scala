package org.josefelixh.couch

import org.mockito.Mockito._
import org.mockito.Matchers._
import org.josefelixh.libs.http.WSRequestHolder
import org.scalatest.mock.MockitoSugar
import scala.concurrent.ExecutionContext

trait CouchMock extends Couch with MockitoSugar {
  implicit val couch = this
  implicit val executionContext = ExecutionContext.Implicits.global

  override val config = CouchConfig("http://couchurl.test", "dbname")

  val requestMock = mock[WSRequestHolder]
  override def couch(path: String): WSRequestHolder = {
    stub(requestMock.url).toReturn(config.couchInstanceUrl+path)
    stub(requestMock.withQueryString(any[(String, String)])).toReturn(requestMock)
    stub(requestMock.withHeaders(any[(String, String)])).toReturn(requestMock)
    requestMock
  }

  lazy val baseUrlMock = s"${config.couchInstanceUrl}/${config.dbName}"
}
