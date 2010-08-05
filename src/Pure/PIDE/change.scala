/*  Title:      Pure/PIDE/change.scala
    Author:     Fabian Immler, TU Munich
    Author:     Makarius

Changes of plain text.
*/

package isabelle


object Change
{
  val init = new Change(Document.NO_ID, None, Nil, Future.value(Nil, Document.init))
}

class Change(
  val id: Document.Version_ID,
  val parent: Option[Change],
  val edits: List[Document.Node.Text_Edit],
  val result: Future[(List[Document.Edit[Command]], Document)])
{
  def ancestors: Iterator[Change] = new Iterator[Change]
  {
    private var state: Option[Change] = Some(Change.this)
    def hasNext = state.isDefined
    def next =
      state match {
        case Some(change) => state = change.parent; change
        case None => throw new NoSuchElementException("next on empty iterator")
      }
  }

  def join_document: Document = result.join._2
  def is_assigned: Boolean = result.is_finished && join_document.assignment.is_finished

  def edit(session: Session, edits: List[Document.Node.Text_Edit]): Change =
  {
    val new_id = session.create_id()
    val result: Future[(List[Document.Edit[Command]], Document)] =
      Future.fork {
        val old_doc = join_document
        old_doc.await_assignment
        Document.text_edits(session, old_doc, new_id, edits)
      }
    new Change(new_id, Some(this), edits, result)
  }
}