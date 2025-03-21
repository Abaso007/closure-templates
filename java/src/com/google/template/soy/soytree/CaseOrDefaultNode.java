/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy.soytree;

import com.google.common.base.Preconditions;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;

/**
 * Abstract node representing a 'case' or 'default' block in 'select', 'switch' or 'plural'
 * statements.
 */
public abstract class CaseOrDefaultNode extends AbstractBlockCommandNode {

  /**
   * @param id The id for this node.
   * @param sourceLocation The node's source location.
   * @param openTagLocation The node's open tag location.
   * @param commandName The name of the Soy command.
   */
  public CaseOrDefaultNode(
      int id, SourceLocation sourceLocation, SourceLocation openTagLocation, String commandName) {
    super(id, sourceLocation, openTagLocation, commandName);
    Preconditions.checkArgument("case".equals(commandName) || "default".equals(commandName));
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  protected CaseOrDefaultNode(CaseOrDefaultNode orig, CopyState copyState) {
    super(orig, copyState);
  }

  @Override
  public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getTagString());
    appendSourceStringForChildren(sb);
    // Note: No end tag.
    return sb.toString();
  }
}
