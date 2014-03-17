/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013 University of Stuttgart                    **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package de.ust.skill.generator.scala.internal

import java.io.PrintWriter
import scala.collection.JavaConversions.asScalaBuffer
import de.ust.skill.generator.scala.GeneralOutputMaker
import de.ust.skill.ir.Declaration
import de.ust.skill.ir.Field
import de.ust.skill.ir.GroundType
import de.ust.skill.ir.ListType
import de.ust.skill.ir.restriction.SingletonRestriction
import de.ust.skill.ir.SetType
import de.ust.skill.ir.VariableLengthArrayType
import de.ust.skill.ir.ConstantLengthArrayType
import de.ust.skill.ir.MapType
import de.ust.skill.ir.Type
trait StateAppenderMaker extends GeneralOutputMaker {
  abstract override def make {
    super.make
    val packageName = if(this.packageName.contains('.')) this.packageName.substring(this.packageName.lastIndexOf('.')+1) else this.packageName;
    val out = open("internal/StateAppender.scala")
    //package
    out.write(s"""package ${packagePrefix}internal

import java.util.Arrays
import java.nio.ByteBuffer

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import ${packagePrefix}_
import ${packagePrefix}internal.streams.OutBuffer
import ${packagePrefix}internal.streams.OutStream

/**
 * Holds state of a write operation.
 *
 * @see SKilL §6
 * @author Timm Felden
 */
private[internal] final class StateAppender(state : SerializableState, out : OutStream) extends SerializationFunctions(state) {
  import SerializationFunctions._

  // make lbpsi map, update data map to contain dynamic instances and create serialization skill IDs for serialization
  // index → bpsi
  val lbpsiMap = new Array[Long](state.pools.length)
  state.pools.foreach {
    case p : BasePool[_] ⇒
      makeLBPSIMap(p, lbpsiMap, 1, { s ⇒ state.poolByName(s).newObjects.size })
      var id = 1L + p.data.size
      for (i ← p.newObjectsInTypeOrderIterator) {
        i.setSkillID(id)
        id += 1L
      }
    case _ ⇒
  }

  override def annotation(ref : SkillType, out : OutStream) {
    if (null == ref) {
      out.put(0.toByte)
      out.put(0.toByte)
    } else {
      val baseName = state.poolByName(ref.getClass.getSimpleName.toLowerCase).basePool.name
      string(baseName, out)
      v64(ref.getSkillID, out)
    }
  }

  /**
   * ****************
   * PHASE 3: WRITE *
   * ****************
   */
  locally {
    // write string block
    state.String.asInstanceOf[StringPool].prepareAndAppend(out, this)

    // write count of the type block
    v64(state.pools.size, out)

    // we have to buffer the data chunk before writing it
    val dataChunk = new OutBuffer();

    // @note performance hack: requires at least 1 instance in order to work correctly
    @inline def genericPutField(p : StoragePool[_ <: SkillType, _ <: SkillType], f : FieldDeclaration, instances : Iterator[SkillType]) {
      f.t match {
        case I8         ⇒ for (i ← instances) i8(i.get(p, f).asInstanceOf[Byte], dataChunk)
        case I16        ⇒ for (i ← instances) i16(i.get(p, f).asInstanceOf[Short], dataChunk)
        case I32        ⇒ for (i ← instances) i32(i.get(p, f).asInstanceOf[Int], dataChunk)
        case I64        ⇒ for (i ← instances) i64(i.get(p, f).asInstanceOf[Long], dataChunk)
        case V64        ⇒ for (i ← instances) v64(i.get(p, f).asInstanceOf[Long], dataChunk)

        case StringType ⇒ for (i ← instances) string(i.get(p, f).asInstanceOf[String], dataChunk)
      }
    }
    @inline def write(p : StoragePool[_ <: SkillType, _ <: SkillType]) {
      p match {${
      (for (d ← IR) yield {
        val sName = d.getSkillName
        val fields = d.getFields.filterNot(_.isIgnored)
        s"""
        case p : ${d.getCapitalName}StoragePool ⇒
          val outData = p.newObjectsInTypeOrderIterator
          val newPool = p.blockInfos.isEmpty

          string("$sName", out)
          if (newPool) {
            ${
          if (null == d.getSuperType) "out.put(0.toByte)"
          else s"""string("${d.getSuperType.getSkillName}", out)
            v64(lbpsiMap(p.poolIndex.toInt), out)"""
        }
          }
          val count = outData.size
          v64(count, out)

          if (newPool)
            restrictions(p, out)

          val fields = if (0 != count) p.fields
          else p.fields.filter(_.dataChunks.isEmpty)
          assert(fields.forall { f ⇒ p.knownFields.contains(f.name) }, "adding instances with an unknown field is currently not supported")

          v64(fields.size, out)

          for (f ← fields) {
            if (f.dataChunks.isEmpty) {
              restrictions(f, out)
              writeType(f.t, out)
              string(f.name, out)

              // bulk chunk
              f.name match {${
          (for (f ← fields) yield s"""
                case "${f.getSkillName()}" ⇒ locally {
                  ${writeField(d, f, s"p.all.asInstanceOf[Iterator[${d.getCapitalName}]]")}
                }""").mkString("")
        }
                case _ ⇒ if (outData.size > 0) genericPutField(p, f, p.allInTypeOrder.asInstanceOf[Iterator[${d.getCapitalName}]])
              }
            } else {
              // simple chunk
              f.name match {${
          (for (f ← fields) yield s"""
                case "${f.getSkillName()}" ⇒ locally {
                  ${writeField(d, f, "outData")}
                }""").mkString("")
        }
                case _ ⇒ if (outData.size > 0) genericPutField(p, f, outData)
              }
            }
            // end
            v64(dataChunk.size, out)
          }"""
      }
      ).mkString("")
    }
        case _ ⇒ locally {
          // generic write
          val newPool = p.blockInfos.isEmpty
          val outData = p.newObjectsInTypeOrderIterator

          string(p.name, out)
          if (newPool) {
            p.superName match {
              case Some(sn) ⇒
                string(sn, out)
                v64(lbpsiMap(p.poolIndex.toInt), out)
              case None ⇒
                out.put(0.toByte)
            }
          }
          val count = outData.size
          v64(count, out)

          if (newPool)
            restrictions(p, out)

          val fields = if (0 != count) p.fields
          else p.fields.filter(_.dataChunks.isEmpty)
          assert(fields.forall { f ⇒ p.knownFields.contains(f.name) }, "adding instances with an unknown field is currently not supported")

          v64(fields.size, out)
          for (f ← fields) {
            if (f.dataChunks.isEmpty) {
              restrictions(f, out)
              writeType(f.t, out)
              string(f.name, out)

              // bulk chunk
              if (p.dynamicSize > 0)
                genericPutField(p, f, p.all.asInstanceOf[Iterator[SkillType]])
            } else {
              // simple chunk
              if (outData.size > 0) {
                genericPutField(p, f, outData)
              }
            }

            // end
            v64(dataChunk.size, out)
          }
        }
      }
    }
    state.pools.foreach(write(_))
    out.putAll(dataChunk)

    out.close
  }
}
""")

    //class prefix
    out.close()
  }

  // field writing helper functions
  private def writeField(d: Declaration, f: Field, iteratorName: String): String = f.getType match {
    case t: GroundType ⇒ t.getSkillName match {

      case "i64" ⇒
        s"""val target = ByteBuffer.allocate(8 * outData.size)
                val it = outData.iterator.asInstanceOf[Iterator[${d.getName}]]
                while (it.hasNext)
                  target.putLong(it.next.${escaped(f.getName)})

                dataChunk.put(target.array)"""

      case _ ⇒ s"for(i ← $iteratorName) ${f.getType().getSkillName()}(i.${escaped(f.getName)}, dataChunk)"
    }

    case t: Declaration ⇒ s"""for(i ← $iteratorName) userRef(i.${escaped(f.getName)}, dataChunk)"""

    case t: ConstantLengthArrayType ⇒ s"$iteratorName.map(_.${escaped(f.getName)}).foreach { instance ⇒ writeConstArray(${
      t.getBaseType() match {
        case t: Declaration ⇒ s"userRef[${mapType(t)}]"
        case b              ⇒ b.getSkillName()
      }
    })(instance, dataChunk) }"
    case t: VariableLengthArrayType ⇒ s"$iteratorName.map(_.${escaped(f.getName)}).foreach { instance ⇒ writeVarArray(${
      t.getBaseType() match {
        case t: Declaration ⇒ s"userRef[${mapType(t)}]"
        case b              ⇒ b.getSkillName()
      }
    })(instance, dataChunk) }"
    case t: SetType ⇒ s"$iteratorName.map(_.${escaped(f.getName)}).foreach { instance ⇒ writeSet(${
      t.getBaseType() match {
        case t: Declaration ⇒ s"userRef[${mapType(t)}]"
        case b              ⇒ b.getSkillName()
      }
    })(instance, dataChunk) }"
    case t: ListType ⇒ s"$iteratorName.map(_.${escaped(f.getName)}).foreach { instance ⇒ writeList(${
      t.getBaseType() match {
        case t: Declaration ⇒ s"userRef[${mapType(t)}]"
        case b              ⇒ b.getSkillName()
      }
    })(instance, dataChunk) }"

    case t: MapType ⇒ locally {
      s"$iteratorName.map(_.${escaped(f.getName)}).foreach { instance ⇒ ${
        t.getBaseTypes().map {
          case t: Declaration ⇒ s"userRef[${mapType(t)}]"
          case b              ⇒ b.getSkillName()
        }.reduceRight { (t, v) ⇒
          s"writeMap($t, $v)"
        }
      }(instance, dataChunk) }"
    }
  }
}