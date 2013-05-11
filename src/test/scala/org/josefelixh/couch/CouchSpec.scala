package org.josefelixh.couch

import org.scalatest.FlatSpec
import org.josefelixh.libs.http._
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import org.scalatest.matchers.MustMatchers
import com.ning.http.client.Realm

class CouchSpec extends FlatSpec with MustMatchers with MockitoSugar {

  "Couch" should "initialise according to configuration" in new Couch {
    override val couchUrl: String = "http://couchurl.com"
    override val dbName: String = "badger"

    val request = db("/path")
    request.url must be === "http://couchurl.com/badger/path"
    request.headers must be === Map("Accept" -> List("application/json"), "Content-Type" -> List("application/json"))
    request.auth.get must be === ("josefelixh", "cloudant123", Realm.AuthScheme.BASIC)
    request.queryString must be === Map()
  }

  it should "use GET http method when calling info" in new CouchMock {
    this.info
    verify(requestMock).get()
  }

  it should "use PUT http method when empty body when calling create" in new CouchMock {
    this.create
    verify(requestMock).put("{}")
  }

  it should "use DELETE http method when calling delete" in new CouchMock {
    this.delete
    verify(requestMock).delete()
  }

  it should "use GET http method and the right url when getting all docs" in new CouchMock {

    override val db = { path: String =>
      path must be === "/_all_docs"
      requestMock
    }

    this.documents
    verify(requestMock).get()
    verifyNoMoreInteractions(requestMock)
  }

  trait CouchMock extends Couch {
    override val dbName: String = "mock"
    val requestMock = mock[WSRequestHolder]
    override def couch(path: String): WSRequestHolder = requestMock
  }

}
