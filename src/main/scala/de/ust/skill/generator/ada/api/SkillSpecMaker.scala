/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013 University of Stuttgart                    **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package de.ust.skill.generator.ada.api

import java.io.PrintWriter
import scala.collection.JavaConversions._
import de.ust.skill.generator.ada.GeneralOutputMaker
import de.ust.skill.ir.Declaration

trait SkillSpecMaker extends GeneralOutputMaker {
  abstract override def make {
    super.make
    val out = open(s"""${packagePrefix}-api-skill.ads""")

    out.write(s"""
with ${packagePrefix.capitalize}.Internal.File_Reader;
with ${packagePrefix.capitalize}.Internal.File_Writer;

package ${packagePrefix.capitalize}.Api.Skill is

${
  var output = "";
  for (d ← IR) {
    output += s"""   type ${d.getName}_Type_Accesses is array (Natural range <>) of ${d.getName}_Type_Access;\r\n"""
  }
  output
}
   procedure Create (State : access Skill_State);
   procedure Read (State : access Skill_State; File_Name : String);
   procedure Write (State : access Skill_State; File_Name : String);

${
  def printParameters(d : Declaration): String = {
    var hasFields = false;
    var output = "";
    output += d.getAllFields.filter({ f ⇒ !f.isConstant && !f.isIgnored }).map({ f =>
      hasFields = true;
      s"${f.getSkillName()} : ${mapType(f.getType)}"
    }).mkString("; ", "; ", "")
    if (hasFields) output else ""
  }

  var output = "";
  for (d ← IR) {
    val parameters = d.getAllFields.filter({ f ⇒ !f.isConstant && !f.isIgnored }).map(f => s"${f.getSkillName()} : ${mapType(f.getType)}").mkString("; ", "; ", "")
    output += s"""   procedure New_${d.getName} (State : access Skill_State${printParameters(d)});\r\n"""
    output += s"""   function Get_${d.getName}s (State : access Skill_State) return ${d.getName}_Type_Accesses;\r\n"""
  }
  output
}
end ${packagePrefix.capitalize}.Api.Skill;
""")

    out.close()
  }
}
