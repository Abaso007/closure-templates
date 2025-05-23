/*
 * Copyright 2008 Google Inc.
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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.basetree.MixinParentNode;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import java.util.List;

/** Abstract implementation of a ParentSoyNode. */
public abstract class AbstractParentSoyNode<N extends SoyNode> extends AbstractSoyNode
    implements ParentSoyNode<N> {

  /** The mixin object that implements the ParentNode functionality. */
  private final MixinParentNode<N> parentMixin;

  /**
   * @param id The id for this node.
   * @param sourceLocation The node's source location.
   */
  public AbstractParentSoyNode(int id, SourceLocation sourceLocation) {
    super(id, sourceLocation);
    parentMixin = new MixinParentNode<>(this);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  protected AbstractParentSoyNode(AbstractParentSoyNode<N> orig, CopyState copyState) {
    super(orig, copyState);
    this.parentMixin = new MixinParentNode<>(orig.parentMixin, this, copyState);
  }

  @Override
  public int numChildren() {
    return parentMixin.numChildren();
  }

  @Override
  public N getChild(int index) {
    return parentMixin.getChild(index);
  }

  @Override
  public int getChildIndex(Node child) {
    return parentMixin.getChildIndex(child);
  }

  @Override
  public List<N> getChildren() {
    return parentMixin.getChildren();
  }

  @Override
  public void addChild(N child) {
    parentMixin.addChild(child);
  }

  @Override
  public void addChild(int index, N child) {
    parentMixin.addChild(index, child);
  }

  @Override
  public void removeChild(int index) {
    parentMixin.removeChild(index);
  }

  @Override
  public void removeChild(N child) {
    parentMixin.removeChild(child);
  }

  @Override
  public void replaceChild(int index, N newChild) {
    parentMixin.replaceChild(index, newChild);
  }

  @Override
  public void replaceChild(N currChild, N newChild) {
    parentMixin.replaceChild(currChild, newChild);
  }

  @Override
  public void clearChildren() {
    parentMixin.clearChildren();
  }

  @Override
  public void addChildren(List<? extends N> children) {
    parentMixin.addChildren(children);
  }

  @Override
  public void addChildren(int index, List<? extends N> children) {
    parentMixin.addChildren(index, children);
  }

  @Override
  public void appendSourceStringForChildren(StringBuilder sb) {
    parentMixin.appendSourceStringForChildren(sb);
  }
}
