package org.josefelixh.couch

import org.scalatest.FlatSpec
import org.scalatest.matchers.MustMatchers
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.{JsObject, Json}
import org.mockito.Mockito._
import scala.concurrent._
import org.josefelixh.libs.http.Response
import concurrent.duration._
import language.postfixOps

class CouchDocumentSpec extends FlatSpec with MustMatchers with MockitoSugar {

  "A CouchDocument" should "create objects" in new CouchMock {

    val responseMock = mock[Response]
    stub(responseMock.json).toReturn(jsonTestObjectSuccessResponse)
    when(requestMock.post(jsonTestObject.toString())).thenReturn(future { responseMock })

    val couchDocument = Await.result(CouchDocument(jsonTestObject).create, 0.1 seconds)

    couchDocument.id.get must be === "test_id"
    couchDocument.rev.get must be === "test_rev"
    couchDocument.doc.get must be === jsonTestObject


    verify(requestMock, times(1)).post(jsonTestObject.toString())
    verifyNoMoreInteractions(requestMock)
    requestMock.url must be === baseUrlMock + "/"
  }

  it should "delete objects" in new CouchMock {
    val responseMock = mock[Response]
    stub(responseMock.json).toReturn(jsonTestObjectSuccessResponse)
    when(requestMock.delete()).thenReturn(future { responseMock })

    Await.result(CouchDocument.Id[JsObject]("test_id").withRev("test_rev").delete, 0.1 seconds)

    requestMock.url must be === baseUrlMock + "/test_id"
  }

  val jsonTestObject = Json.obj(
    "key1" -> "value1",
    "key2" -> "value2",
    "key3" -> "value3"
  )

  val jsonTestObjectSuccessResponse = Json.obj(
    "ok" -> true,
    "id" -> "test_id",
    "rev" -> "test_rev"
  )
}
