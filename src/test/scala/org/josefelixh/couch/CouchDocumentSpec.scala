package org.josefelixh.couch

import org.scalatest.FlatSpec
import org.scalatest.matchers.MustMatchers
import org.scalatest.mock.MockitoSugar
import play.api.libs.json._
import org.mockito.Mockito._
import scala.concurrent._
import concurrent.duration._
import language.postfixOps
import org.josefelixh.libs.http.Response
import play.api.libs.json.JsObject

class CouchDocumentSpec extends FlatSpec with MustMatchers with MockitoSugar {

  "A CouchDocument object" should "persist the document when calling create" in new CouchMock {

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

  it should "delete the document when calling delete" in new CouchMock {
    val responseMock = mock[Response]
    stub(responseMock.json).toReturn(jsonTestObjectSuccessResponse)
    when(requestMock.delete()).thenReturn(future { responseMock })

    val deleted = Await.result(CouchDocument("test_id", jsonTestObject).withRev("test_rev").delete, 0.1 seconds)
    deleted.id must be === None
    deleted.rev must be === None
    deleted.doc.get must be === jsonTestObject
    requestMock.url must be === baseUrlMock + "/test_id"
  }

  it should "retrieve the document when calling retrieve" in new CouchMock {
    val responseMock = mock[Response]
    stub(responseMock.json).toReturn(jsonTestObjectResponse)
    when(requestMock.get()).thenReturn(future { responseMock })

    val documentId = CouchDocument.Id[JsObject]("test_id")
    val couchDocument = Await.result(documentId.retrieve, 0.1 seconds)

    requestMock.url must be === baseUrlMock + "/test_id"

    couchDocument.id.get must be === "test_id"
    couchDocument.rev.get must be === "test_rev"
    couchDocument.doc.get must be === jsonTestObject
  }

  it should "update the document when calling update" in new CouchMock {
    val responseMock = mock[Response]
    stub(responseMock.json).toReturn(jsonTestObjectSuccessResponse)
    when(requestMock.put(updatedJsonTestObject.toString())).thenReturn(future { responseMock })

    val couchDocument = Await.result(CouchDocument("test_id", jsonTestObject).withRev("rev").update(_ => updatedJsonTestObject), 0.1 seconds)

    requestMock.url must be === baseUrlMock + "/test_id"

    couchDocument.id.get must be === "test_id"
    couchDocument.rev.get must be === "test_rev"
    couchDocument.doc.get must be === updatedJsonTestObject
  }

  it should "correctly parse objects" in new CouchMock {

    implicit val testObjectFormat = Json.format[TestObject]
    val responseMock = mock[Response]
    stub(responseMock.json).toReturn(jsonTestObjectResponse)
    when(requestMock.get()).thenReturn(future { responseMock })

    val couchDocument = Await.result(CouchDocument.Id[TestObject]("test_id").retrieve, 0.1 seconds)

    couchDocument.id.get must be === "test_id"
    couchDocument.rev.get must be === "test_rev"
    couchDocument.doc.get must be === testObject
  }

  case class TestObject(key1: String, key2: String, key3: String)
  val testObject = TestObject("value1", "value2", "value3")
  val jsonTestObject = Json.obj(
    "key1" -> "value1",
    "key2" -> "value2",
    "key3" -> "value3"
  )

  val updatedJsonTestObject = Json.obj(
    "key1" -> "updatedValue1",
    "key2" -> "updatedValue2",
    "key3" -> "updatedValue3"
  )

  val jsonTestObjectSuccessResponse = Json.obj(
    "ok" -> true,
    "id" -> "test_id",
    "rev" -> "test_rev"
  )

  val jsonTestObjectResponse = Json.obj(
    "_id" -> "test_id",
    "_rev" -> "test_rev",
    "key1" -> "value1",
    "key2" -> "value2",
    "key3" -> "value3"
  )
}
