package org.josefelixh.couch

import org.josefelixh.libs.http.{Codec, WS}
import com.ning.http.client.Realm

object Couch {
  def apply(db: String): Couch = new Couch(db)
}
class Couch(private val dbName: String) {

  val couchUrl = "https://josefelixh.cloudant.com"
  val couch: String => WS.WSRequestHolder = { path =>
    WS.url(s"$couchUrl$path")
      .withAuth("josefelixh", "l0nd0nJoel", Realm.AuthScheme.BASIC)
      .withHeaders(
        "Accept" -> "application/json",
        "Content-Type" -> "application/json"
    )
  }
  val db: String => WS.WSRequestHolder = { path => couch(s"/$dbName$path") }

  def databases = couch("/_all_dbs").get()
  def info = db("/").get()
  def create = db("/").put("{}")
  def delete = db("/").delete()
  def documents = db("/_all_docs").get()
}
