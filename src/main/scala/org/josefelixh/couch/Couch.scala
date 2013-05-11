package org.josefelixh.couch

import org.josefelixh.libs.http._
import com.ning.http.client.Realm

object Couch {
  def apply(dbname: String): Couch = new Couch {
    override val dbName = dbname
  }
}
trait Couch {
  val dbName: String

  val couchUrl = "https://josefelixh.cloudant.com"
  def couch(path: String): WSRequestHolder =
    WS.url(s"$couchUrl$path")
      .withAuth("josefelixh", "cloudant123", Realm.AuthScheme.BASIC)
      .withHeaders(
        "Accept" -> "application/json",
        "Content-Type" -> "application/json"
    )

  val db: String => WSRequestHolder = { path => couch(s"/$dbName$path") }

  def info = db("/").get()
  def create = db("/").put("{}")
  def delete = db("/").delete()
  def documents = db("/_all_docs").get()

  object DB {
    def apply(dbName: String): DB = new DB(dbName)
  }
  class DB(dbName: String) {


  }



  def databases = couch("/_all_dbs").get()

}
