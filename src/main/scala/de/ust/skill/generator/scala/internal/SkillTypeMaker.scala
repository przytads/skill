/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013 University of Stuttgart                    **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package de.ust.skill.generator.scala.internal

import de.ust.skill.generator.scala.GeneralOutputMaker

trait SkillTypeMaker extends GeneralOutputMaker {
  abstract override def make {
    super.make
    val packageName = if(this.packageName.contains('.')) this.packageName.substring(this.packageName.lastIndexOf('.')+1) else this.packageName;
    val out = open("internal/SkillType.scala")
    //package & imports
    out.write(s"""package ${packagePrefix}internal

import ${packagePrefix}api.Access

/**
 * The top of the skill type hierarchy.
 * @author Timm Felden
 */
class SkillType private[$packageName] (protected var skillID : Long) {
  private[internal] final def getSkillID = skillID
  private[internal] final def setSkillID(newID : Long) = skillID = newID

  /**
   * mark an instance as deleted
   */
  final def delete = setSkillID(0)

  /**
   * checks for a deleted mark
   */
  final def markedForDeletion = 0 == getSkillID

  /**
   * provides a pretty representation of this
   */
  def prettyString : String = s"<some fully generic type#$$skillID>"

  /**
   * reflective setter
   */
  def set[@specialized T](acc : Access[_ <: SkillType], field : FieldDeclaration, value : T) {
    acc.asInstanceOf[StoragePool[_ <: SkillType, _ <: SkillType]].unknownFieldData(field).put(this, value)
  }

  /**
   * reflective getter
   */
  def get(acc : Access[_ <: SkillType], field : FieldDeclaration) : Any = {
    try {
      acc.asInstanceOf[StoragePool[_ <: SkillType, _ <: SkillType]].unknownFieldData(field)(this)
    } catch {
      case e : Exception ⇒ this+" is not in:\\n"+acc.asInstanceOf[StoragePool[_ <: SkillType, _ <: SkillType]].unknownFieldData(field).mkString("\\n")
    }
  }
}

object SkillType {
  final class SubType private[$packageName] (val τName : String, skillID : Long) extends SkillType(skillID)  with NamedType{
    override def prettyString : String = τName+"(this: "+this+")"
    override def toString = τName+"#"+skillID
  }
}

trait NamedType {
  val τName : String;
}
""")

    out.close()
  }
}
