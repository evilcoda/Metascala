package svm.model

import org.objectweb.asm.tree._
import org.objectweb.asm.Label


object Method {
  def read(mn: MethodNode) = {
    implicit val labelMap = Code.makeLabelMap(mn.instructions)
    Method(
      mn.access,
      mn.name,
      Type.Desc.read(mn.desc),
      mn.exceptions.safeList,
      Code.read(mn.instructions),
      Misc(
        mn.signature.safeOpt,
        mn.tryCatchBlocks.safeList.map(TryCatchBlock.read),
        mn.localVariables.safeList.map(LocalVariable.read),
        mn.maxStack,
        mn.maxLocals,
        mn.attrs.safeList.map(Attribute.read)
      ),
      Annotations(
        mn.visibleAnnotations.safeList.map(Annotation.read),
        mn.invisibleAnnotations.safeList.map(Annotation.read),
        mn.annotationDefault.safeOpt,
        mn.visibleParameterAnnotations.safeList.map(_.safeList.map(Annotation.read)),
        mn.invisibleParameterAnnotations.safeList.map(_.safeList.map(Annotation.read))
      )
    )
  }

  case class Annotations(visibleAnnotations: List[Annotation] = Nil,
                         invisibleAnnotations: List[Annotation] = Nil,
                         annotationDefault: Any = null,
                         visibleParameterAnnotations: List[List[Annotation]] = Nil,
                         invisibleParameterAnnotations: List[List[Annotation]] = Nil)
  case class Misc(signature: Option[String] = None,
                  tryCatchBlocks: List[TryCatchBlock] = Nil,
                  localVariables: List[LocalVariable] = Nil,
                  maxStack: Int = 0,
                  maxLocals: Int = 0,
                  attrs: List[Attribute] = Nil)
}
case class Method(access: Int,
                  name: String,
                  desc: Type.Desc,
                  exceptions: List[String] = Nil,
                  code: Code = Code(),
                  misc: Method.Misc = Method.Misc(),
                  annotations: Method.Annotations = Method.Annotations())

object Code{


  def makeLabelMap(nodesList: InsnList): Map[Label, Int] = {
    val nodes = nodesList.toArray

    val labelMapMaker = collection.mutable.Map.empty[Label, Int]
    var i = 0
    for(node <- nodes){
      node match{
        case x: LabelNode => labelMapMaker(x.getLabel) = i
        case y if OpCode.read(Map.empty[Label, Int]).isDefinedAt(y) => i += 1
        case _ => ()
      }
    }
    labelMapMaker.toMap
  }
  def read(nodesList: InsnList)(implicit labelMap: Map[Label, Int]) = {

    val nodes = nodesList.toArray
    var instructions: List[OpCode] = Nil
    var allAttached: List[List[Attached]] = Nil
    var attached: List[Attached] = Nil

    for(node <- nodes){
      OpCode.read.andThen{o =>
        instructions ::= o
        allAttached ::= attached
        attached = Nil
      } orElse Attached.read.andThen{ a =>
        attached ::= a
      } lift(node)
    }

    Code(instructions.reverse, allAttached.reverse)
  }
}

case class Code(instructions: List[OpCode] = Nil,
                attachments: List[List[Attached]] = Nil)

trait Attached
object Attached{
  case class Frame(frameType: Int,
                   local: List[Any],
                   stack: List[Any]) extends Attached

  case class LineNumber(line: Int,
                        start: Int) extends Attached



  def read(implicit labelMap: Map[Label, Int]): PartialFunction[Any, Attached] = {
    case x: FrameNode       => Frame(x.`type`, x.local.safeList, x.stack.safeList)
    case x: LineNumberNode  => LineNumber(x.line, x.start.getLabel)
  }
}
