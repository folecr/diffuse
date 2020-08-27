package com.jakewharton.diffuse.info

import com.jakewharton.diffuse.ArchiveFile
import com.jakewharton.diffuse.ArchiveFiles
import com.jakewharton.diffuse.Size
import com.jakewharton.diffuse.diffuseTable
import com.jakewharton.picnic.TableSectionDsl
import com.jakewharton.picnic.TextAlignment
import com.jakewharton.picnic.renderText

internal fun ArchiveFiles.toSummaryTable(
  name: String,
  displayTypes: List<ArchiveFile.Type>,
  skipIfEmptyTypes: Set<ArchiveFile.Type> = emptySet(),
  includeCompressed: Boolean = true,
) = diffuseTable {
  header {
    if (includeCompressed) {
      row {
        cell(name) {
          alignment = TextAlignment.BottomLeft
        }
        cell("compressed") {
          alignment = TextAlignment.BottomCenter
        }
        cell("uncompressed") {
          alignment = TextAlignment.BottomCenter
        }
      }
    } else {
      row {
        cell(name) {
          alignment = TextAlignment.BottomLeft
        }
        cell("size")
      }
    }
  }

  fun TableSectionDsl.addApkRow(name: String, type: ArchiveFile.Type? = null) {
    val old = if (type != null) filterValues { it.type == type } else this@toSummaryTable
    val oldSize = old.values.fold(Size.ZERO) { acc, file -> acc + file.size }
    val oldUncompressedSize = old.values.fold(Size.ZERO) { acc, file -> acc + file.uncompressedSize }
    if (oldSize != Size.ZERO || type !in skipIfEmptyTypes) {
      if (includeCompressed) {
        row(
          name,
          oldSize,
          oldUncompressedSize,
        )
      } else {
        row(name, oldUncompressedSize)
      }
    }
  }

  body {
    cellStyle {
      alignment = TextAlignment.MiddleRight
    }
    for (type in displayTypes) {
      addApkRow(type.displayName, type)
    }
  }

  footer {
    cellStyle {
      alignment = TextAlignment.MiddleRight
    }
    addApkRow("total")
  }
}.renderText()