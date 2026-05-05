package dev.isxander.mtk.accessx.writer

import dev.isxander.mtk.accessx.AccessEntry
import dev.isxander.mtk.accessx.AccessFile
import dev.isxander.mtk.accessx.AccessFormat
import dev.isxander.mtk.accessx.AccessNamespace
import java.io.Writer

internal class FabricAccessFileWriter(
    private val format: AccessFormat,
) : AccessFileWriter {
    init {
        if (format == AccessFormat.AT) {
            error("AT format is not supported by FabricAccessFileWriter")
        }
    }

    override fun writeFile(file: AccessFile, writer: Writer): Boolean {
        val namespace = when (file.namespace) {
            AccessNamespace.Named -> "named"
            AccessNamespace.Intermediary -> "intermediary"
            AccessNamespace.Official -> "official"
        }

        val header = when (format) {
            AccessFormat.AW_V1 -> "accessWidener\tv1\t$namespace"
            AccessFormat.AW_V2 -> "accessWidener\tv2\t$namespace"
            AccessFormat.CT_V1 -> "classTweaker\tv1\t$namespace"
            AccessFormat.CT_V2 -> "classTweaker\tv2\t$namespace"
            else -> return false
        }

        writer.appendLine(header)

        for (entry in file.entries) {
            if (!this.writeEntry(entry, writer)) {
                return false
            }
        }

        return true
    }

    override fun writeEntry(entry: AccessEntry, writer: Writer): Boolean {
        when (entry) {
            is AccessEntry.AccessModifier -> {
                fun writeEntry(modifier: String) {
                    if (entry.transitive) {
                        writer.write("transitive-")
                    }
                    writer.append(modifier).append('\t')

                    writer.appendLine(when (entry) {
                        is AccessEntry.AccessModifier.Class -> "class\t${entry.className}"
                        is AccessEntry.AccessModifier.Method -> "method\t${entry.className} ${entry.methodName} ${entry.methodDescriptor}"
                        is AccessEntry.AccessModifier.Field -> "field\t${entry.className} ${entry.fieldName} ${entry.fieldDescriptor}"
                    })
                }

                if (entry.final == false) {
                    writeEntry(if (entry is AccessEntry.AccessModifier.Field) "mutable" else "extendable")

                    // An "extendable" class is implicitly also "accessible"
                    if (entry is AccessEntry.AccessModifier.Class) {
                        return true
                    }
                }
                if (entry.modification == AccessEntry.AccessModifier.Modification.Public) {
                    writeEntry("accessible")
                }
            }
            is AccessEntry.InjectInterface -> {
                writer
                    .append(if (entry.transitive) "transitive-" else "")
                    .append("inject-interface\t")
                    .append(entry.targetClass)
                    .append('\t')
                    .appendLine(entry.interfaceToInject)
            }
            is AccessEntry.ExtendEnum -> {
                writer
                    .append(if (entry.transitive) "transitive-" else "")
                    .append("extend-enum\t")
                    .append(entry.targetClass)
                    .append('\t')
                    .appendLine(entry.enumConstant)
            }
        }

        return true
    }
}
