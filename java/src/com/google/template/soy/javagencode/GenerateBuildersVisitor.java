/*
 * Copyright 2019 Google Inc.
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

package com.google.template.soy.javagencode;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.template.soy.base.SourceLocation.UNKNOWN;
import static com.google.template.soy.javagencode.javatypes.CodeGenUtils.ADD_TO_LIST_PARAM;
import static com.google.template.soy.javagencode.javatypes.CodeGenUtils.AS_RECORD;
import static com.google.template.soy.javagencode.javatypes.CodeGenUtils.INDIRECT_P;
import static com.google.template.soy.javagencode.javatypes.CodeGenUtils.INIT_LIST_PARAM;
import static com.google.template.soy.javagencode.javatypes.CodeGenUtils.INJECTED_P;
import static com.google.template.soy.javagencode.javatypes.CodeGenUtils.SET_PARAM_INTERNAL;
import static com.google.template.soy.javagencode.javatypes.CodeGenUtils.STANDARD_P;
import static com.google.template.soy.javagencode.javatypes.CodeGenUtils.maybeAddNullableToClass;
import static com.google.template.soy.shared.internal.gencode.JavaGenerationUtils.appendFunctionCallWithParamsOnNewLines;
import static com.google.template.soy.shared.internal.gencode.JavaGenerationUtils.appendJavadoc;
import static com.google.template.soy.shared.internal.gencode.JavaGenerationUtils.isReservedKeyword;
import static com.google.template.soy.shared.internal.gencode.JavaGenerationUtils.makeLowerCamelCase;
import static com.google.template.soy.shared.internal.gencode.JavaGenerationUtils.makeUpperCamelCase;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation.ByteSpan;
import com.google.template.soy.base.internal.KytheMode;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.javagencode.SoyFileNodeTransformer.FileInfo;
import com.google.template.soy.javagencode.SoyFileNodeTransformer.ParamInfo;
import com.google.template.soy.javagencode.SoyFileNodeTransformer.ParamStatus;
import com.google.template.soy.javagencode.SoyFileNodeTransformer.TemplateInfo;
import com.google.template.soy.javagencode.javatypes.CodeGenUtils;
import com.google.template.soy.javagencode.javatypes.CollectionJavaType;
import com.google.template.soy.javagencode.javatypes.FutureJavaType;
import com.google.template.soy.javagencode.javatypes.JavaType;
import com.google.template.soy.javagencode.javatypes.RecordJavaType;
import com.google.template.soy.shared.internal.gencode.GeneratedFile;
import com.google.template.soy.shared.internal.gencode.IndentedLinesBuilder;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.TemplateHeaderVarDefn;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Visitor for generating Java template parameter builders (see {@link
 * com.google.template.soy.data.BaseSoyTemplateImpl}) that can be used for invoking Soy templates
 * from Java. One java file will be generated for each soy file, containing template param builders
 * for each template in the soy file.
 *
 * <p>For example, "foo.soy" containing templates "bar" and "baz" would result in FooTemplates.java,
 * with inner classes Bar and Baz.
 */
public final class GenerateBuildersVisitor
    extends AbstractSoyNodeVisitor<ImmutableList<GeneratedFile>> {

  private static final String TEMPLATE_NAME_FIELD = "__NAME__";
  private static final String PARAMS_FIELD = "__PARAMS__";
  private static final String DEFAULT_INSTANCE_FIELD = "__DEFAULT_INSTANCE__";

  private static final SoyErrorKind TYPE_COLLISION =
      SoyErrorKind.of(
          "Parameter ''{0}'' in {1} has different types in different templates. No parameter"
              + " setter generated.");
  private static final SoyErrorKind INDIRECT_PROTO =
      SoyErrorKind.of(
          "Parameter ''{0}'' in {1} depends on a proto or proto enum that is not a direct"
              + " dependency of this library. No parameter setter generated.");
  private static final SoyErrorKind TEMPLATE_NAME_COLLISION =
      SoyErrorKind.of(
          "When generating Soy Java Template Builders, the template: {0} generated the same Java"
              + " UpperCamelCase name as another template in this file, or collided with a"
              + " reserved identifier: "
              + SoyFileNodeTransformer.RESERVED_IDENTIFIERS
              + ". This template was skipped during Soy java_builders generation. To use this API,"
              + " all Soy template names in a given file should be unique when converted to"
              + " UpperCamelCase (with non-alphanumeric characters stripped). The generated Java"
              + " class name was: {1}.");
  private static final SoyErrorKind PARAM_NAME_COLLISION =
      SoyErrorKind.of(
          "When generating Soy Java Template Builders, the param named {0} in template {1}"
              + " generated the same UpperCamelCase name as another parameter, or collided with"
              + " a reserved identifier: "
              + SoyFileNodeTransformer.RESERVED_IDENTIFIERS
              + ". Param: {0} is being skipped (no setters will be generated for this param). The"
              + " generated setter name was: {2}. To use this API, all parameter names for a given"
              + " template should be unique when converted to UpperCamelCase (with"
              + " non-alphanumeric characters stripped).");
  private static final SoyErrorKind SETTER_SIGNATURE_COLLISION =
      SoyErrorKind.of(
          "When generating Soy Java Template Builders, the param named {0} in template {1}"
              + " generated a setter with the same signature as another setter for the same param: "
              + "`{2}`. This can happen with unions since one setter for each union member is "
              + "generated. In case of collisions, a setter for the first type, when read left-"
              + "-to-right, is emitted.");
  private static final SoyErrorKind FILE_NAME_COLLISION =
      SoyErrorKind.of(
          "While generating Soy Java invocation builders, multiple files in this soy fileset"
              + " mapped to the same file name: {0}. To use this api, soy file names should be"
              + " unique when converted to UpperCamelCase (with non-alpha-numeric characters"
              + " stripped).");
  private static final SoyErrorKind FUTURE_NAME_COLLISION =
      SoyErrorKind.of(
          "Achievement unlocked. You have a template with parameters named {0} and"
              + " {0}Future, preventing a future setter from being created for the first"
              + " parameter.");

  private final ErrorReporter errorReporter;
  private final KytheMode kytheMode;
  private final SoyFileNodeTransformer transformer;

  private IndentedLinesBuilder ilb; // Line formatter for the generated code.
  private KytheHelper kytheHelper;
  private ImmutableList.Builder<GeneratedFile> generatedFiles; // The generated Java files to write.

  public GenerateBuildersVisitor(
      ErrorReporter errorReporter,
      String javaPackage,
      KytheMode kytheMode,
      FileSetMetadata registry) {
    this.errorReporter = errorReporter;
    this.kytheMode = kytheMode;
    this.transformer = new SoyFileNodeTransformer(javaPackage, registry);
  }

  @Override
  public ImmutableList<GeneratedFile> exec(SoyNode node) {
    generatedFiles = new ImmutableList.Builder<>();
    ilb = null;
    kytheHelper = null;
    visit(node);

    ImmutableList<GeneratedFile> builtFileList = generatedFiles.build();
    logWarningIfFilenamesNotUnique(builtFileList);
    return builtFileList;
  }

  @Override
  protected void visitSoyFileSetNode(SoyFileSetNode node) {
    for (SoyFileNode soyFile : node.getChildren()) {
      visit(soyFile);
    }
  }

  @Override
  protected void visitSoyFileNode(SoyFileNode soyFile) {
    FileInfo fileInfo = transformer.transform(soyFile);
    kytheHelper = new KytheHelper(soyFile.getFilePath());
    ilb = new IndentedLinesBuilder(kytheHelper);
    appendFileHeaderAndImports(fileInfo);

    String javaClassNameForSoyFile = fileInfo.className();

    // Start of *FooTemplates class.
    appendJavadoc(
        ilb,
        "Wrapper class containing {@link com.google.template.soy.data.SoyTemplate} builders for"
            + " each template in: "
            + fileInfo.soyFileName()
            + ".",
        /* forceMultiline= */ false,
        /* wrapAt100Chars= */ true);
    ilb.appendLine(
        "@javax.annotation.Generated(\n"
            + "    value = \"com.google.template.soy.SoyParseInfoGenerator\""
            + (kytheMode.isEnabled()
                ? ",\n    comments = \"kythe-inline-metadata:kythe-inline-metadata\""
                : "")
            + ")");

    ilb.appendLineStart("public final class ")
        .appendImputee(javaClassNameForSoyFile, getByteSpan(soyFile))
        .appendLineEnd(" {");

    ilb.increaseIndent();

    // Add FooParams subclasses for the templates in this file.
    generateParamsClassesForEachTemplate(fileInfo);

    // End of *FooTemplates class.
    ilb.decreaseIndent();
    ilb.appendLine("}");

    String kytheComment = getKytheComment(kytheMode, kytheHelper);

    // Add the file name and contents to the list of generated files to write.
    String fileName = javaClassNameForSoyFile + ".java";
    generatedFiles.add(GeneratedFile.create("", fileName, ilb + kytheComment));
    ilb = null;
  }

  private static ByteSpan getByteSpan(SoyFileNode node) {
    return SoyTreeUtils.getByteSpan(node, node.getNamespaceDeclaration().getNamespaceLocation());
  }

  private static ByteSpan getByteSpan(TemplateInfo node) {
    return SoyTreeUtils.getByteSpan(node.template(), node.template().getTemplateNameLocation());
  }

  private ByteSpan getByteSpan(TemplateInfo template, ParamInfo param) {
    TemplateNode t = template.template();
    Optional<TemplateHeaderVarDefn> match =
        t.getHeaderParams().stream().filter(p -> p.name().equals(param.name())).findFirst();
    return match.isPresent()
        ? SoyTreeUtils.getByteSpan(template.template(), match.get().nameLocation())
        : ByteSpan.UNKNOWN;
  }

  static String getKytheComment(KytheMode kytheMode, KytheHelper kytheHelper) {
    StringBuilder sb = new StringBuilder();
    kytheHelper.appendGeneratedCodeInfo(kytheMode, sb);
    return sb.toString();
  }

  /** For each public, non-delegate template in the given soy file, generates a Foo inner class. */
  private void generateParamsClassesForEachTemplate(FileInfo soyFile) {
    soyFile
        .templates()
        .forEach(
            t -> {
              switch (t.status()) {
                case HANDLED:
                  visitTemplateInfo(t);
                  break;
                case NAME_COLLISION:
                  errorReporter.warn(
                      t.sourceLocation(), TEMPLATE_NAME_COLLISION, t.templateName(), t.className());
                  break;
              }
            });
  }

  /**
   * Writes a Foo subclass for the given template. The class extends {@link
   * com.google.template.soy.data.BaseSoyTemplateImpl}, which implements {@link
   * com.google.template.soy.data.SoyTemplate}.
   */
  private void visitTemplateInfo(TemplateInfo template) {
    String templateClass = template.className();

    // Start of Foo class.
    String templateDescription = template.soyDocDesc();
    ilb.appendLine();
    appendJavadoc(
        ilb,
        "Template params for "
            + template.templateNameForUserMsgs()
            + (templateDescription != null ? ": " + templateDescription : "."),
        /* forceMultiline= */ false,
        /* wrapAt100Chars= */ true);

    ilb.appendLineStart("public static final class ")
        .appendImputee(templateClass, getByteSpan(template))
        .appendLineEnd(" extends com.google.template.soy.data.BaseSoyTemplateImpl {");

    ilb.increaseIndent();
    ilb.appendLine();
    ilb.appendLine(
        "private static final java.lang.String "
            + TEMPLATE_NAME_FIELD
            + " = \""
            + template.templateName()
            + "\";");
    ilb.appendLine();

    appendFutureWrapperMethod(templateClass);

    // Constructor for Foo.
    ilb.appendLine("private " + templateClass + "(" + templateClass + ".Builder builder) {");
    ilb.increaseIndent();
    ilb.appendLine("super(builder);");
    ilb.decreaseIndent();
    ilb.appendLine("}");

    ilb.appendLine();

    ilb.appendLine("@java.lang.Override");
    ilb.appendLine("public final java.lang.String getTemplateName() {");
    ilb.increaseIndent();
    ilb.appendLine("return " + TEMPLATE_NAME_FIELD + ";");
    ilb.decreaseIndent();
    ilb.appendLine("}");
    ilb.appendLine();

    appendParamsBuilderClass(template, templateClass);

    // End of Foo class.
    ilb.decreaseIndent();
    ilb.appendLine("}");
    ilb.appendLine();
  }

  /**
   * Adds a static method to each Params class: {@code public static SoyTemplate.AsyncWrapper<Foo>
   * wrapFuture(ListenableFuture<Foo>)}. This utility is needed for supporting Producers + some Apps
   * Framework utility classes.
   *
   * @see com.google.apps.framework.template.StructuredPageResponse
   */
  private void appendFutureWrapperMethod(String paramsClass) {
    appendJavadoc(
        ilb,
        "Wraps a ListenableFuture<"
            + paramsClass
            + "> as a SoyTemplate.AsyncWrapper<"
            + paramsClass
            + ">",
        false,
        true);
    ilb.appendLine(
        "public static com.google.template.soy.data.SoyTemplate.AsyncWrapper<"
            + paramsClass
            + "> wrapFuture(com.google.common.util.concurrent.ListenableFuture<"
            + paramsClass
            + "> paramsFuture) {");
    ilb.increaseIndent();
    ilb.appendLine(
        "return new com.google.template.soy.data.SoyTemplate.AsyncWrapper<>("
            + TEMPLATE_NAME_FIELD
            + ", paramsFuture);");
    ilb.decreaseIndent();
    ilb.appendLine("}");
    ilb.appendLine();
  }

  /**
   * Appends a builder class for template "foo" with parameter setting methods. This class extends
   * the {@link com.google.template.soy.data.BaseSoyTemplateImpl.AbstractBuilder} class.
   */
  private void appendParamsBuilderClass(TemplateInfo template, String templateParamsClassname) {
    appendJavadoc(ilb, "Creates a new Builder instance.", false, true);
    ilb.appendLineStart("public static Builder ")
        .appendImputee("builder", getByteSpan(template))
        .appendLineEnd("() {");
    ilb.increaseIndent();
    ilb.appendLine("return new Builder();");
    ilb.decreaseIndent();
    ilb.appendLine("}");
    ilb.appendLine();

    // Will contain handled and unhandled params. We include some types of unhandled params so that
    // they still end up in the generated list of params.
    List<ParamInfo> combinedParams =
        template.params().stream()
            .filter(
                info -> {
                  switch (info.status()) {
                    case HANDLED:
                    case UNHANDLED_TYPE:
                      return true;
                    case NAME_COLLISION:
                      errorReporter.warn(
                          info.sourceLocation(),
                          PARAM_NAME_COLLISION,
                          info.name(),
                          template.templateName(),
                          info.setterName());
                      return true;
                    case JAVA_INCOMPATIBLE:
                      break;
                    case INDIRECT_INCOMPATIBLE_TYPES:
                      errorReporter.warn(
                          info.sourceLocation(),
                          TYPE_COLLISION,
                          info.name(),
                          template.templateName());
                      break;
                    case INDIRECT_PROTO:
                      errorReporter.warn(
                          info.sourceLocation(),
                          INDIRECT_PROTO,
                          info.name(),
                          template.templateName());
                      break;
                  }
                  return false;
                })
            .collect(toList());

    appendParamConstants(ilb, template, combinedParams);

    List<ParamInfo> nonInjectedParams =
        combinedParams.stream().filter(p -> !p.injected()).collect(toList());

    if (nonInjectedParams.stream().noneMatch(ParamInfo::requiredAndNotIndirect)) {
      // Invoke the constructor directly. For these templates it could allow callers to avoid
      // loading the builder completely.
      ilb.appendLine(
          "private static final "
              + templateParamsClassname
              + " "
              + DEFAULT_INSTANCE_FIELD
              + " = new "
              + templateParamsClassname
              + "(builder());");
      ilb.appendLine();

      appendJavadoc(
          ilb,
          "Creates a new instance of "
              + templateParamsClassname
              + " with no parameters set. This method was generated because all template"
              + " parameters are optional.",
          false,
          true);
      ilb.appendLineStart("public static ", templateParamsClassname, " ")
          .appendImputee("getDefaultInstance", getByteSpan(template))
          .appendLineEnd("() {");
      ilb.increaseIndent();
      ilb.appendLine("return " + DEFAULT_INSTANCE_FIELD + ";");
      ilb.decreaseIndent();
      ilb.appendLine("}");
      ilb.appendLine();
    }

    boolean anyAccumulatorParameters =
        nonInjectedParams.stream()
            .flatMap(param -> param.javaTypes().stream())
            .anyMatch(
                javaType ->
                    javaType instanceof RecordJavaType && ((RecordJavaType) javaType).isList());
    // Start of Foo.Builder class.
    ilb.appendLineStart("public static final class ")
        .appendImputee("Builder", getByteSpan(template))
        .appendLineEnd(
            " extends com.google.template.soy.data.BaseSoyTemplateImpl.",
            (anyAccumulatorParameters
                ? "AbstractBuilderWithAccumulatorParameters"
                : "AbstractBuilder"),
            "<Builder, ",
            templateParamsClassname,
            "> {");
    ilb.appendLine();
    ilb.increaseIndent();

    // Constructor for Foo.Builder.
    ilb.appendLine("private Builder() {");
    ilb.increaseIndent();
    ilb.appendLine(
        "super(",
        nonInjectedParams.size(),
        ", "
            + nonInjectedParams.stream().filter(pi -> pi.required() && !pi.indirect()).count()
            + ");");
    appendRecordListInitializations(ilb, nonInjectedParams);
    ilb.decreaseIndent();
    ilb.appendLine("}");
    ilb.appendLine();

    // #allParams() for FooTemplate.Builder.
    ilb.appendLine("@java.lang.Override");
    ilb.appendLine(
        "protected"
            + " com.google.common.collect.ImmutableSet<com.google.template.soy.data.SoyTemplateParam<?>>"
            + " allParams() {");
    ilb.increaseIndent();
    ilb.appendLine("return " + PARAMS_FIELD + ";");
    ilb.decreaseIndent();
    ilb.appendLine("}");
    ilb.appendLine();

    // #build() for FooTemplate.Builder.
    ilb.appendLine("@java.lang.Override");
    ilb.appendLine("public " + templateParamsClassname + " build() {");
    ilb.increaseIndent();
    // Flush any accumulator parameters.
    if (anyAccumulatorParameters) {
      ilb.appendLine("prepareDataForBuild();");
    }
    ilb.appendLine("return new " + templateParamsClassname + "(this);");
    ilb.decreaseIndent();
    ilb.appendLine("}");
    ilb.appendLine();

    // #buildPartial() for FooTemplate.Builder.
    ilb.appendLine("@java.lang.Override");
    ilb.appendLine("public com.google.template.soy.data.PartialSoyTemplate buildPartial() {");
    ilb.increaseIndent();
    if (anyAccumulatorParameters) {
      ilb.appendLine("prepareDataForBuild();");
    }
    ilb.appendLine("return doBuildPartial(" + TEMPLATE_NAME_FIELD + ");");
    ilb.decreaseIndent();
    ilb.appendLine("}");

    // Add setters for each direct template param.
    nonInjectedParams.stream()
        .filter(p -> p.status() == ParamStatus.HANDLED)
        .forEach(p -> writeSettersForParam(p, template));

    ilb.appendLine();

    // End of FooTemplateInvocation.Builder class.
    ilb.decreaseIndent();
    ilb.appendLine("}");
  }

  private void appendParamConstants(
      IndentedLinesBuilder ilb, TemplateInfo template, List<ParamInfo> params) {
    Set<String> usedNames = new HashSet<>();
    List<String> nonInjected = new ArrayList<>();
    for (ParamInfo param : params) {
      while (usedNames.contains(param.constantFieldName())) {
        param.updateConstantFieldName();
      }
      String fieldName = param.constantFieldName();
      usedNames.add(fieldName);
      if (!param.injected()) {
        nonInjected.add(fieldName);
      }

      String genericType = "?";
      List<JavaType> types = param.javaTypes();
      if (types.size() == 1) {
        JavaType javaType = types.get(0);
        // this is basically 'instanceof RecordJavaType' at this point
        if (javaType.isTypeLiteralSupported()) {
          genericType = javaType.asTypeLiteralString();
        }
      }

      // Make any param that supports type literal public so it can be used with
      // TemplateParamModule, SoyTemplateData, AbstractBuilder, and tests. Union types, records, and
      // CSS params will be private since they can't be represented as a single specific type
      // literal.
      String visibility = !"?".equals(genericType) ? "public" : "private";

      // These values correspond to static factory methods on SoyTemplateParam.
      CodeGenUtils.Member factory = STANDARD_P;
      if (param.injected()) {
        factory = INJECTED_P;
      } else if (param.indirect()) {
        factory = INDIRECT_P;
      }

      String paramDescription = param.param().getDescription();
      if (paramDescription == null) {
        paramDescription = "";
      } else {
        paramDescription += " ";
      }

      String typeToken =
          "?".equals(genericType)
              // TODO(jcg): this should probably be a wildcard type
              ? "com.google.common.reflect.TypeToken.of(java.lang.Object.class)"
              : (genericType.matches("(\\.|\\w)+")
                  ? "com.google.common.reflect.TypeToken.of(" + genericType + ".class" + ")"
                  : "new com.google.common.reflect.TypeToken<" + genericType + ">() {}");
      ilb.appendLine(
          String.format(
              "/** {@%s %s} %s*/",
              param.injected() ? "inject" : "param", param.name(), paramDescription));
      ilb.appendLine(
          String.format(
              "%s static final com.google.template.soy.data.SoyTemplateParam<%s>",
              visibility, genericType));
      ilb.increaseIndent(2);
      ilb.appendLineStart()
          .appendImputee(fieldName, getByteSpan(template, param))
          .appendLineEnd(" =");
      ilb.increaseIndent(2);
      ilb.appendLine(factory, "(");
      ilb.increaseIndent(2);
      ilb.appendLine("\"", param.name(), "\",");
      ilb.appendLine("/* required= */ ", param.required(), ",");
      ilb.appendLine(typeToken, ");");
      ilb.decreaseIndent(6);
      ilb.appendLine();
    }

    ilb.appendLineStart(
        "private static final"
            + " com.google.common.collect.ImmutableSet<com.google.template.soy.data.SoyTemplateParam<?>>"
            + " "
            + PARAMS_FIELD
            + " = ");
    // Omit injected params from the list of params passed to the builder.
    appendFunctionCallWithParamsOnNewLines(
        ilb, "com.google.common.collect.ImmutableSet.of", nonInjected);
    ilb.appendLineEnd(";");
    ilb.appendLine();
  }

  private static void appendRecordListInitializations(
      IndentedLinesBuilder ilb, List<ParamInfo> params) {
    // For every required param that's of type list<[...]> (list of records), initialize the list
    // so that upon building the template we do not throw an error for zero records.
    for (ParamInfo param : params) {
      if (param.required()) {
        List<JavaType> types = param.javaTypes();
        if (types.size() == 1
            && types.get(0) instanceof RecordJavaType
            && ((RecordJavaType) types.get(0)).isList()) {
          ilb.appendLine(String.format("%s(%s);", INIT_LIST_PARAM, param.constantFieldName()));
        }
      }
    }
  }

  /** Appends the file header and imports for the generated *FooTemplates.java */
  private void appendFileHeaderAndImports(FileInfo soyFile) {
    // Header.
    ilb.appendLine("// This file was automatically generated by the Soy compiler.");
    ilb.appendLine("// Please don't edit this file by hand.");
    ilb.appendLine("// source: " + soyFile.soyFilePath().path()); // For Code Search link.
    ilb.appendLine();
    ilb.appendLine("package " + soyFile.packageName() + ";");
    ilb.appendLine();
    ilb.appendLine();

    // No Imports!
    // It is annoying and verbose but by fully qualifying all type names we can avoid conflicts
    // with user defined symbols
  }

  /**
   * Writes setter methods each of the java types that this param can be (e.g union int | string
   * would generate setFoo(int) and setFoo(string)).
   *
   * <p>TODO(b/77550695): Update docs for how we handle futures.
   */
  private void writeSettersForParam(ParamInfo param, TemplateInfo template) {
    // Add setters for this param.
    Set<String> signatures = new HashSet<>();
    for (JavaType javaType : param.javaTypes()) {
      String signature =
          javaType instanceof RecordJavaType
              ? ((RecordJavaType) javaType)
                  .getJavaTypeMap().values().stream()
                      .map(JavaType::toJavaTypeString)
                      .collect(joining(","))
              : javaType.toJavaTypeString();
      // Collisions are possible in unions containing records with a single property or unions with
      // records with the same types.
      if (!signatures.add(signature)) {
        errorReporter.warn(
            param.sourceLocation(),
            SETTER_SIGNATURE_COLLISION,
            param.name(),
            template.templateName(),
            String.format("%s(%s)", param.setterName(), signature));
      } else {
        writeSetter(param, template, javaType);
      }
    }

    // For now only write the future interface if the setter is not already overloaded
    switch (param.futureStatus()) {
      case HANDLED:
        for (JavaType futureType : param.futureTypes()) {
          writeFutureSetter(ilb, param, template, new FutureJavaType(futureType));
          if (futureType instanceof CollectionJavaType) {
            CollectionJavaType collectionType = (CollectionJavaType) futureType;
            writeCollectionFutureSetter(
                ilb,
                param,
                template,
                new CollectionJavaType(
                    collectionType.getSubtype(),
                    new FutureJavaType(collectionType.getElementType()),
                    collectionType.isNullable()));
          }
        }
        break;
      case NAME_COLLISION:
        errorReporter.warn(param.sourceLocation(), FUTURE_NAME_COLLISION, param.name());
        break;
      case UNHANDLED:
        break;
    }
  }

  /** Writes a setter method for the given param and java type. */
  private void writeSetter(ParamInfo param, TemplateInfo template, JavaType javaType) {
    String paramDescription = param.param().getDescription();
    ilb.appendLine();
    appendJavadoc(
        ilb,
        "Sets "
            + param.name()
            + (Strings.isNullOrEmpty(paramDescription) ? "." : ": " + paramDescription),
        /* forceMultiline= */ false,
        /* wrapAt100Chars= */ true);

    if (javaType instanceof RecordJavaType) {
      writeRecordSetter(param, template, (RecordJavaType) javaType);
    } else {
      String javaTypeString = javaType.toJavaTypeString();
      boolean nullable = javaType.isNullable();

      ilb.appendLine("@com.google.errorprone.annotations.CanIgnoreReturnValue");
      ilb.appendLineStart("public Builder ")
          .appendImputee(param.setterName(), getByteSpan(template, param))
          .appendLineEnd("(", maybeAddNullableToClass(nullable, javaTypeString), " value) {");
      ilb.increaseIndent();

      String newVariableName = javaType.asInlineCast("value");
      ilb.appendLine(
          "return " + SET_PARAM_INTERNAL + "(",
          param.constantFieldName(),
          ", ",
          newVariableName,
          ");");
      ilb.decreaseIndent();
      ilb.appendLine("}");
    }
  }

  private void writeRecordSetter(ParamInfo param, TemplateInfo template, RecordJavaType type) {
    ilb.appendLine("@com.google.errorprone.annotations.CanIgnoreReturnValue");

    ilb.appendLineStart("public Builder ")
        .appendImputee(
            type.isList() ? param.adderName() : param.setterName(), getByteSpan(template, param))
        .appendLineMiddle("(");

    List<String> paramNames = type.getJavaTypeMap().keySet().asList();
    List<String> javaParamNames = new ArrayList<>();

    boolean first = true;
    for (Map.Entry<String, JavaType> entry : type.getJavaTypeMap().entrySet()) {
      String paramName = makeParamName(entry.getKey());
      javaParamNames.add(paramName);

      if (!first) {
        ilb.appendLineMiddle(", ");
      }
      JavaType paramType = entry.getValue();
      ilb.appendLineMiddle(
          maybeAddNullableToClass(paramType.isNullable(), paramType.toJavaTypeString()),
          " ",
          paramName);
      first = false;
    }
    ilb.appendLineEnd(") {");
    ilb.increaseIndent();

    CodeGenUtils.Member delegate = type.isList() ? ADD_TO_LIST_PARAM : SET_PARAM_INTERNAL;

    ilb.appendLineStart(
        "return ", delegate, "(", param.constantFieldName(), ", " + AS_RECORD + "(");
    int numParams = paramNames.size();
    for (int i = 0; i < numParams; i++) {
      if (i != 0) {
        ilb.appendLineMiddle(", ");
      }
      ilb.appendLineMiddle(
          "\"",
          paramNames.get(i),
          "\", ",
          type.getJavaTypeMap().get(paramNames.get(i)).asInlineCast(javaParamNames.get(i)));
    }
    ilb.appendLineEnd("));");
    ilb.decreaseIndent();
    ilb.appendLine("}");
  }

  /** Writes a setter method for the given param and java type. */
  private void writeFutureSetter(
      IndentedLinesBuilder ilb, ParamInfo param, TemplateInfo template, FutureJavaType javaType) {

    ilb.appendLine();
    appendJavadoc(
        ilb,
        "Future compatible version of {@link #"
            + param.setterName()
            + "("
            + stripGenerics(javaType.getType().toJavaTypeString())
            + ")}.",
        /* forceMultiline= */ false,
        /* wrapAt100Chars= */ true);
    ilb.appendLine("@com.google.errorprone.annotations.CanIgnoreReturnValue");

    ilb.appendLineStart("public Builder ")
        .appendImputee(param.futureSetterName(), getByteSpan(template, param))
        .appendLineEnd("(", javaType.toJavaTypeString(), " future) {");

    ilb.increaseIndent();

    ilb.appendLine(
        "return "
            + SET_PARAM_INTERNAL
            + "("
            + param.constantFieldName()
            + ", "
            + javaType.asInlineCast("future")
            + ");");
    ilb.decreaseIndent();
    ilb.appendLine("}");
  }

  /** Writes a setter method for the given param and java type. */
  private void writeCollectionFutureSetter(
      IndentedLinesBuilder ilb,
      ParamInfo param,
      TemplateInfo template,
      CollectionJavaType javaType) {

    ilb.appendLine();
    appendJavadoc(
        ilb,
        "Future compatible collection version of {@link #"
            + param.setterName()
            + "("
            + stripGenerics(javaType.toJavaTypeString())
            + ")}.",
        /* forceMultiline= */ false,
        /* wrapAt100Chars= */ true);
    ilb.appendLine("@com.google.errorprone.annotations.CanIgnoreReturnValue");

    String variableName = makeLowerCamelCase(javaType.getSubtype().name());

    ilb.appendLineStart("public Builder ")
        .appendImputee(
            param.futureSetterName() + makeUpperCamelCase(javaType.getSubtype().name()),
            getByteSpan(template, param))
        .appendLineEnd("(", javaType.toJavaTypeString(), " " + variableName + ") {");

    ilb.increaseIndent();

    ilb.appendLine(
        "return "
            + SET_PARAM_INTERNAL
            + "("
            + param.constantFieldName()
            + ", "
            + javaType.asInlineCast(variableName)
            + ");");
    ilb.decreaseIndent();
    ilb.appendLine("}");
  }

  private static String stripGenerics(String type) {
    String newType = type;
    do {
      type = newType;
      newType = type.replaceAll("<[^>]*>*", "");
    } while (!newType.equals(type));
    return newType;
  }

  /** Logs a warning if two soy files mapped to the same generated java file name. */
  private void logWarningIfFilenamesNotUnique(ImmutableList<GeneratedFile> files) {
    ImmutableList<String> duplicateFilenames =
        files.stream().collect(groupingBy(GeneratedFile::fileName, counting())).entrySet().stream()
            .filter(e -> e.getValue() > 1) // We only care about duplicate filenames.
            .map(Map.Entry::getKey)
            .collect(toImmutableList());

    for (String fileName : duplicateFilenames) {
      errorReporter.warn(UNKNOWN, FILE_NAME_COLLISION, fileName);
    }
  }

  private static String makeParamName(String s) {
    s = makeLowerCamelCase(s);
    return isReservedKeyword(s) ? s + "_" : s;
  }
}
