package scorex.core.diagnostics

import java.io.{File, PrintWriter}

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import scorex.core.consensus.SyncInfo
import scorex.core.network.SendingStrategy
import scorex.core.network.message.{InvData, Message, ModifiersData}
import scorex.util.ScorexLogging

class DiagnosticsActor extends Actor with ScorexLogging {

  import DiagnosticsActor.ReceivableMessages._

  private val outWriter = new PrintWriter(new File(s"/tmp/ergo/out-messages-${context.system.startTime}.json"))
  private val inWriter = new PrintWriter(new File(s"/tmp/ergo/in-messages-${context.system.startTime}.json"))

  override def preStart(): Unit = log.info("Starting diagnostics actor...")

  override def postStop(): Unit = {
    outWriter.close()
    inWriter.close()
  }

  override def receive: Receive = {
    case OutNetworkMessage(Message(spec, Right(data), _), strategy, receivers, timestamp) =>
      receivers.foreach { r =>
        val record =
          s"""{"timestamp":$timestamp,"msgType":"${spec.messageName}","data":${decodeData(data)},"strategy":"$strategy","receiver":"$r"},\n""".stripMargin
        outWriter.write(record)
      }

    case InNetworkMessage(Message(spec, Right(data), _), sender, timestamp) =>
      val record =
        s"""{"timestamp":$timestamp,"msgType":"${spec.messageName}","data":${decodeData(data)},"sender":"$sender"},\n""".stripMargin
      outWriter.write(record)

    case other =>
      log.info(s"DiagnosticsActor: unknown message: $other")
  }

  private def decodeData(data: Any) = data match {
    case InvData(typeId, ids) =>
      s"""{"typeId":"$typeId","ids":[${ids.map(id => s""""$id"""").mkString(",")}]}"""
    case ModifiersData(typeId, mods) =>
      s"""{"typeId":"$typeId","ids":[${mods.keys.map(id => s""""$id"""").mkString(",")}]}"""
    case si: SyncInfo =>
      val ids = si.startingPoints
      s"""{"typeId":"101","ids":[${ids.map(id => s""""${id._2}"""").mkString(",")}]}"""
    case other =>
      s""""?$other""""
  }

}

object DiagnosticsActor {

  object ReceivableMessages {

    case class OutNetworkMessage(msg: Message[_], strategy: SendingStrategy, receivers: Seq[String], timestamp: Long)

    case class InNetworkMessage(msg: Message[_], sender: String, timestamp: Long)

  }

}

object DiagnosticsActorRef {
  def apply(name: String)(implicit system: ActorSystem): ActorRef = system.actorOf(Props[DiagnosticsActor], name)
}
