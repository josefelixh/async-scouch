package org.josefelixh.couch

import play.api.libs.functional._
import play.api.libs.json._
import scala.concurrent._
import org.josefelixh.libs.http.Response

object CouchDocument {

  def Id[T](id: String)(implicit fmt: Format[T]): CouchDocument[T] = {
    val doc: Option[T] = None
    CouchDocument(Some(id), None, doc)
  }

  def apply[T](t: T)(implicit couch: Couch,  fmt: Format[T], execCtx: ExecutionContext): CouchDocument[T] =
    CouchDocument[T](None, None, Some(t))

  def apply[T](id: String, t: T)(implicit couch: Couch,  fmt: Format[T], execCtx: ExecutionContext): CouchDocument[T] =
    CouchDocument[T](Some(id), None, Some(t))

}

case class CouchDocument[T](id: Option[String] = None, rev: Option[String] = None, doc: Option[T])(implicit fmt: Format[T]) {

  def create(implicit couch: Couch, execCtx: ExecutionContext): Future[CouchDocument[T]] = {
    val jsDoc = this.doc match {
      case Some(d) => Json.toJson(d).transform(AddCouchIdToJson).asOpt
      case None => Json.parse("{}").asOpt
    }
    couch.db("/").post(jsDoc.get.toString()) map { IdAndRevisionDocument }
  }

  def retrieve(implicit couch: Couch, execCtx: ExecutionContext): Future[CouchDocument[T]] =
    couch.db(s"/${id.get}").get() map { revisionAndDocument }

  def delete(implicit couch: Couch) =
    couch.db(s"/${id.get}").withQueryString(
      "rev" -> rev.get
    ).delete()

  def update(updator: T => T)(implicit couch: Couch, execCtx: ExecutionContext): Future[CouchDocument[T]] = {
    val updatedDoc = updator(this.doc.get)
    couch.db(s"/${id.get}").withQueryString(
      "rev" -> rev.get
    ).withHeaders(
      "If-Match" -> rev.get
    ).put(Json.toJson(updatedDoc).toString()) map {
      revision(updatedDoc)
    }
  }

  def withId(x: String) = this.copy(id = Some(x))
  def withRev(x: String) = this.copy(rev = Some(x))
  def withDoc[A](doc: A)(implicit fmt: Format[A]) = new CouchDocument(this.id, this.rev, Some(doc))

  override def toString = {
    s"CouchDocument(_id = ${id.get}, _rev = ${rev.get}, ${doc.get}"
  }

  private def revisionAndDocument(response: Response): CouchDocument[T] = {
    response.json.transform((__ \ 'ok).json.prune andThen ((__ \ '_id).json.prune andThen ((__ \ '_rev).json.prune))) map { js =>
      this.copy(
        rev = (response.json \ "_rev").validate[String].asOpt,
        doc = js.validate[T](implicitly[Reads[T]]).asOpt
      )
    } getOrElse(throw new RuntimeException)
  }

  private def revision(updated: T)(response: Response): CouchDocument[T] = {
    this.copy(
      rev = (response.json \ "rev").validate[String].asOpt,
      doc = Some(updated)
    )
  }

  private val IdAndRevisionDocument: Response => CouchDocument[T] = { response =>
    (response.json \ "ok").validate[Boolean] map { isOk =>
      if (!isOk) throw new RuntimeException
    }

    this.copy(
      id = (response.json \ "id").validate[String].asOpt,
      rev = (response.json \ "rev").validate[String].asOpt
    )
  }

  private val AddCouchIdToJson = {
    val addCouchId =  __.json.update {
      __.read[JsObject] map { o => Json.obj("_id" -> id.get) ++ o }
    }

    id match {
      case Some(couchId) => addCouchId
      case None => __.read[JsObject]
    }
  }
}