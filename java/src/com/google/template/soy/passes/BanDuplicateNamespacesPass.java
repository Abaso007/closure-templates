/*
 * Copyright 2021 Google Inc.
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

package com.google.template.soy.passes;

import static com.google.common.collect.ImmutableSetMultimap.toImmutableSetMultimap;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.internal.exemptions.NamespaceExemptions;
import com.google.template.soy.passes.CompilerFileSetPass.Result;
import com.google.template.soy.soytree.FileMetadata;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateNode;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Reports an error if two source files have the same namespace.
 *
 * <p>This is a limited check since conflicts may be in completely separate compilation units.
 */
@RunAfter(FinalizeTemplateRegistryPass.class)
final class BanDuplicateNamespacesPass implements CompilerFileSetPass {
  private static final SoyErrorKind DUPLICATE_NAMESPACE =
      SoyErrorKind.of(
          "Found another file ''{0}'' with the same namespace.  All files must have unique"
              + " namespaces.");
  private static final SoyErrorKind DUPLICATE_NAMESPACE_WARNING =
      SoyErrorKind.of(
          "Found another file ''{0}'' with the same namespace.  All files should have unique"
              + " namespaces. This will soon become an error.");
  private static final SoyErrorKind NAMESPACE_COLLISION =
      SoyErrorKind.of("Template ''{0}'' collides with namespace ''{1}'' declared in ''{2}''.");
  private final ErrorReporter errorReporter;
  private final Supplier<FileSetMetadata> fileSetTemplateRegistry;

  BanDuplicateNamespacesPass(
      ErrorReporter errorReporter, Supplier<FileSetMetadata> fileSetTemplateRegistry) {
    this.errorReporter = errorReporter;
    this.fileSetTemplateRegistry = fileSetTemplateRegistry;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator nodeIdGen) {
    ImmutableSetMultimap<String, String> namespaceToFiles =
        fileSetTemplateRegistry.get().getAllFiles().stream()
            .filter(
                f -> !f.getNamespace().equals(TemplateNode.SoyFileHeaderInfo.EMPTY.getNamespace()))
            .collect(toImmutableSetMultimap(FileMetadata::getNamespace, f -> f.getPath().path()));
    for (SoyFileNode sourceFile : sourceFiles) {
      ImmutableSet<String> filePaths = namespaceToFiles.get(sourceFile.getNamespace());
      if (filePaths.size() > 1) {
        String filePath = sourceFile.getFilePath().path();
        String otherFiles =
            filePaths.stream().filter(path -> !path.equals(filePath)).collect(joining(", "));
        if (NamespaceExemptions.isKnownDuplicateNamespace(sourceFile.getNamespace())) {
          errorReporter.warn(
              sourceFile.getNamespaceDeclaration().getSourceLocation(),
              DUPLICATE_NAMESPACE_WARNING,
              otherFiles);
        } else {
          errorReporter.report(
              sourceFile.getNamespaceDeclaration().getSourceLocation(),
              DUPLICATE_NAMESPACE,
              otherFiles);
        }
      }
    }

    // Check for template/namespace collisions by sorting all template names. If a template matches
    // all or some of a namespace, they will be adjacent in the sorted set.
    TreeSet<TemplateMetadata> allTemplatesSortedByName =
        new TreeSet<>(comparing(TemplateMetadata::getTemplateName));
    allTemplatesSortedByName.addAll(fileSetTemplateRegistry.get().getAllTemplates());
    TemplateMetadata last = null;
    for (TemplateMetadata next : allTemplatesSortedByName) {
      if (last != null && next.getTemplateName().startsWith(last.getTemplateName() + ".")) {
        errorReporter.report(
            last.getSourceLocation(),
            NAMESPACE_COLLISION,
            last.getTemplateName(),
            namespace(next),
            next.getSourceLocation().getFilePath().toString());
      }
      last = next;
    }
    return Result.CONTINUE;
  }

  private static String namespace(TemplateMetadata meta) {
    return meta.getTemplateName().substring(0, meta.getTemplateName().lastIndexOf('.'));
  }
}
