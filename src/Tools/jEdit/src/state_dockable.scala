/*  Title:      Tools/jEdit/src/state_dockable.scala
    Author:     Makarius

Dockable window for proof state output.
*/

package isabelle.jedit


import isabelle._

import scala.swing.{Button, CheckBox}
import scala.swing.event.ButtonClicked

import java.awt.BorderLayout
import java.awt.event.{ComponentEvent, ComponentAdapter}

import org.gjt.sp.jedit.View


class State_Dockable(view: View, position: String) extends Dockable(view, position)
{
  GUI_Thread.require {}


  /* text area */

  val pretty_text_area = new Pretty_Text_Area(view)
  set_content(pretty_text_area)

  override def detach_operation = pretty_text_area.detach_operation

  private val print_state =
    new Query_Operation(PIDE.editor, view, "print_state", _ => (),
      (snapshot, results, body) =>
        pretty_text_area.update(snapshot, results, Pretty.separate(body)))


  /* resize */

  private val delay_resize =
    GUI_Thread.delay_first(PIDE.options.seconds("editor_update_delay")) { handle_resize() }

  addComponentListener(new ComponentAdapter {
    override def componentResized(e: ComponentEvent) { delay_resize.invoke() }
    override def componentShown(e: ComponentEvent) { delay_resize.invoke() }
  })

  private def handle_resize()
  {
    GUI_Thread.require {}

    pretty_text_area.resize(
      Font_Info.main(PIDE.options.real("jedit_font_scale") * zoom.factor / 100))
  }


  /* update */

  def update()
  {
    GUI_Thread.require {}

    PIDE.document_model(view.getBuffer).map(_.snapshot()) match {
      case Some(snapshot) =>
        (PIDE.editor.current_command(view, snapshot), print_state.get_location) match {
          case (Some(command1), Some(command2)) if command1.id == command2.id =>
          case _ => print_state.apply_query(Nil)
        }
      case None =>
    }
  }


  /* controls */

  private val update_button = new Button("<html><b>Update</b></html>") {
    tooltip = "Update display according to the command at cursor position"
    reactions += { case ButtonClicked(_) => print_state.apply_query(Nil) }
  }

  private val locate_button = new Button("Locate") {
    tooltip = "Locate printed command within source text"
    reactions += { case ButtonClicked(_) => print_state.locate_query() }
  }

  private val zoom = new Font_Info.Zoom_Box { def changed = handle_resize() }

  private val controls =
    new Wrap_Panel(Wrap_Panel.Alignment.Right)(update_button, locate_button,
      pretty_text_area.search_label, pretty_text_area.search_field, zoom)
  add(controls.peer, BorderLayout.NORTH)

  override def focusOnDefaultComponent { update_button.requestFocus }


  /* main */

  private val main =
    Session.Consumer[Any](getClass.getName) {
      case _: Session.Global_Options =>
        GUI_Thread.later { handle_resize() }
    }

  override def init()
  {
    PIDE.session.global_options += main
    handle_resize()
    print_state.activate()
  }

  override def exit()
  {
    print_state.deactivate()
    PIDE.session.global_options -= main
    delay_resize.revoke()
  }
}