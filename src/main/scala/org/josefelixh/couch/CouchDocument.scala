package org.josefelixh.couch

import play.api.libs.functional._
import play.api.libs.json._
import scala.concurrent.{ExecutionContext, Future}
import org.josefelixh.libs.http.Response

object CouchDocument {

  def Id[T](id: String)(implicit fmt: Format[T]): CouchDocument[T] = {
    val doc: Option[T] = None
    CouchDocument(Some(id), None, doc)
  }

  def create[T](t: T)(implicit couch: Couch,  fmt: Format[T], execCtx: ExecutionContext): Future[CouchDocument[T]] =
    CouchDocument[T](None, None, Some(t)).create

  implicit def toCouchDocument[T](t: T)(implicit fmt: Format[T]): CouchDocument[T] =
    CouchDocument(None, None, Some(t))

  implicit def tuple2ToCouchDocument[T](rt: (String, T))(implicit fmt: Format[T]): CouchDocument[T] =
    CouchDocument(Some(rt._1), None, Some(rt._2))

  implicit def tuple3ToCouchDocument[T](rt: (String, String, T))(implicit fmt: Format[T]): CouchDocument[T] =
    CouchDocument(Some(rt._1), Some(rt._2), Some(rt._3))
}

case class CouchDocument[T](id: Option[String] = None, rev: Option[String] = None, doc: Option[T])(implicit fmt: Format[T]) {

  private def create(implicit couch: Couch, execCtx: ExecutionContext): Future[CouchDocument[T]] = {
    val jsDoc = Json.toJson(this.doc.get).transform(AddCouchIdToJson).asOpt
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