/*
 * Copyright (C) 2019 Maksym Oliinyk.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.oliynick.max.tea.core.debug.app.presentation.component

import com.oliynick.max.tea.core.debug.app.presentation.misc.*
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreeModel

class StateTreeRenderer(
    var formatter: ValueFormatter
) : TreeCellRenderer {

    override fun getTreeCellRendererComponent(
        tree: JTree,
        value: Any,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component =
        JLabel().apply {
            val payload = (value as DefaultMutableTreeNode).userObject as RenderTree

            text = payload.toReadableString(tree.model, formatter)
            icon = payload.icon
        }

        /*val label = JLabel()
        val node = value as DefaultMutableTreeNode

        if (node === tree.model.root) {
            label.text = node.userObject.toString()
            return label
        }

        val payload = node.userObject as RenderTree

        label.text = when (payload) {
            RootNode -> "State"
            is SnapshotNode, is MessageNode, is StateNode -> error("Can't render $payload")
            is PropertyNode -> payload.toReadableString(formatter)
            is ValueNode -> payload.toReadableString(formatter)
            is IndexedNode -> payload.toReadableString(formatter)
            is EntryKeyNode -> payload.toReadableString(formatter)
            is EntryValueNode -> payload.toReadableString(formatter)
        }

        label.icon = payload.icon*/

    //return label
    //}

}

private fun RenderTree.toReadableString(
    model: TreeModel,
    formatter: ValueFormatter
): String =
    when (this) {
        RootNode -> "Snapshots (${model.getChildCount(model.root)})"
        is SnapshotNode, is MessageNode, is StateNode -> error("Can't render $this")
        is PropertyNode -> toReadableString(formatter)
        is ValueNode -> toReadableString(formatter)
        is IndexedNode -> toReadableString(formatter)
        is EntryKeyNode -> toReadableString(formatter)
        is EntryValueNode -> toReadableString(formatter)
    }
