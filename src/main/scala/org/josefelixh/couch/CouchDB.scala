package org.josefelixh.couch

import org.josefelixh.libs.http.WS
import com.ning.http.client.Realm

object CouchDB {
  def apply: CouchDB = new CouchDB("")
  def apply(db: String): CouchDB = new CouchDB(db)
}
class CouchDB(private val db: String) {

  val couchUrl = "https://josefelixh.cloudant.com"
  val couch: String => WS.WSRequestHolder = { path =>
    WS.url(s"$couchUrl/$path")
      .withAuth("josefelixh", "l0nd0nJoel", Realm.AuthScheme.BASIC)
      .withHeaders(
        "Accept" -> "application/json",
        "Content-Type" -> "application/json"
    )
  }
  val couchDb = couch(db)
  def databases = couch("_all_dbs").get()
  def info = couchDb.get()
  def create = couchDb.put("{}")
  def delete = couchDb.delete()
}
