package org.josefelixh.couch

case class CouchDocument(id: Option[String] = None, rev: Option[String] = None, doc: String) {

  def toJson: String = id match {
    case Some(x) => {
      val trimmedDoc = doc.trim
      s"""{"_id":"${x}",${trimmedDoc.substring(1)}"""

    }
    case None => doc
  }

  def create(implicit couch: Couch) = {
    couch.db("/").post(this.toJson)
  }

  def retrieve(implicit couch: Couch) = {
    couch.db(s"/${id.get}").get()
  }

  def delete(implicit couch: Couch) = {
    couch.db(s"/${id.get}").withQueryString(
      "rev" -> rev.get
    ).delete()
  }

  def update(implicit couch: Couch) = {
    couch.db(s"/${id.get}").withQueryString(
      "rev" -> rev.get
    ).withHeaders(
      "If-Match" -> rev.get
    ).put(this.toJson)
  }

}
