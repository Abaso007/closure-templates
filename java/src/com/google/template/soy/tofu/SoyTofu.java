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

package com.google.template.soy.tofu;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableSortedSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyInjector;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyTemplate;
import com.google.template.soy.data.SoyTemplateData;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.parseinfo.SoyTemplateInfo;
import com.google.template.soy.parseinfo.TemplateName;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyIdRenamingMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * SoyTofu is the public interface for a Java object that represents a compiled Soy file set.
 *
 * @deprecated Use SoySauce instead. All users should be able to switch from
 *     SoyFileSet.compileToTofu() to SoyFileSet.compileTemplates(). To use the support for
 *     precompilation (outside of apps/framework) see SoySauceBuilder.
 */
@Deprecated
public interface SoyTofu {

  /**
   * Queries the current SoyTofu instance to see if it holds a given template. If the requested
   * template is found, `true` is returned, otherwise, `false`.
   *
   * @param namespace Namespace to check for a template.
   * @return Whether the template exists or not.
   */
  Boolean hasTemplate(String namespace);

  /**
   * Gets a new SoyTofu instance with a different namespace (or no namespace). Note: The new SoyTofu
   * instance will still be backed by the same compiled Soy file set.
   *
   * @param namespace The namespace for the new SoyTofu instance, or null for no namespace.
   * @return A new SoyTofu instance with a different namespace (or no namespace).
   */
  SoyTofu forNamespace(@Nullable String namespace);

  /**
   * Gets a new Renderer for a template.
   *
   * <p>The usage pattern is
   * soyTofu.newRenderer(...).setData(...).setInjectedData(...).setMsgBundle(...).render() where any
   * of the set* parts can be omitted if it's null.
   *
   * @param templateInfo Info for the template to render.
   * @return A new renderer for the given template.
   */
  Renderer newRenderer(SoyTemplateInfo templateInfo);

  /**
   * Gets a new Renderer for a template.
   *
   * <p>The usage pattern is
   * soyTofu.newRenderer(...).setData(...).setInjectedData(...).setMsgBundle(...).render() where any
   * of the set* parts can be omitted if it's null.
   *
   * @param templateName The name of the template to render. If this SoyTofu instance is not
   *     namespaced, then this parameter should be the full name of the template including the
   *     namespace. If this SoyTofu instance is namespaced, then this parameter should be a partial
   *     name beginning with a dot (e.g. ".fooTemplate").
   * @return A new renderer for the given template.
   */
  Renderer newRenderer(String templateName);

  default Renderer newRenderer(TemplateName templateName) {
    return newRenderer(templateName.name());
  }

  /**
   * Returns a new {@link Renderer} for configuring and rendering the given template. The returned
   * renderer will have its data set and may not allow additional calls to {@link Renderer#setData}.
   */
  @Beta
  default Renderer newRenderer(SoyTemplate params) {
    return newRenderer(params.getTemplateName()).setData(params.getParamsAsMap());
  }

  /**
   * Gets the set of injected param keys used by a template (and its transitive callees).
   *
   * @param templateInfo Info for the template to get injected params of.
   * @return The set of injected param keys used by the given template.
   */
  ImmutableSortedSet<String> getUsedIjParamsForTemplate(SoyTemplateInfo templateInfo);

  /**
   * Gets the set of injected param keys used by a template (and its transitive callees).
   *
   * @param templateName The name of the template to get injected params of.
   * @return The set of injected param keys used by the given template.
   */
  ImmutableSortedSet<String> getUsedIjParamsForTemplate(String templateName);

  // -----------------------------------------------------------------------------------------------
  // Renderer interface.

  /** Renderer for a template. */
  interface Renderer {

    /** Sets the data to call the template with. Can be null if the template has no parameters. */
    @CanIgnoreReturnValue
    Renderer setData(Map<String, ?> data);

    /**
     * Sets the data to call the template with. Can be null if the template has no parameters.
     *
     * @deprecated Use {@link #setData(Map)} instead. Soy's custom {@link SoyRecord} type offers no
     *     benefits over native Java maps.
     */
    @Deprecated
    @CanIgnoreReturnValue
    Renderer setData(SoyRecord data);

    /** Sets the injected data to call the template with. Can be null if not used. */
    @CanIgnoreReturnValue
    default Renderer setIjData(Map<String, ?> record) {
      return setIjData(record == null ? null : SoyInjector.fromStringMap(record));
    }

    @CanIgnoreReturnValue
    default Renderer setIjData(SoyTemplateData data) {
      return setIjData(SoyInjector.fromParamStore((ParamStore) data.getParamsAsRecord()));
    }

    @CanIgnoreReturnValue
    Renderer setIjData(SoyInjector data);

    /**
     * Sets the injected data to call the template with. Can be null if not used.
     *
     * @deprecated Use {@link #setIjData(Map)} instead. Soy's custom {@link SoyRecord} type offers
     *     no benefits over native Java maps.
     */
    @Deprecated
    @CanIgnoreReturnValue
    default Renderer setIjData(SoyRecord ijData) {
      return setIjData(SoyInjector.fromRecord(ijData));
    }

    /**
     * Sets the plugin instances that will be used to for method calls from {@code
     * SoyJavaSourceFunctions} that called {@code JavaValueFactory.callInstanceMethod}.
     *
     * <p>Most plugin instances should be associated with the Tofu instance during construction, but
     * this method can be used to add more if that is not feasible or desireable.
     */
    @CanIgnoreReturnValue
    Renderer setPluginInstances(Map<String, ? extends Supplier<Object>> pluginInstances);

    /** Sets the predicate to use for testing whether or not a given {@code mod} is active. */
    @CanIgnoreReturnValue
    Renderer setActiveModSelector(Predicate<String> activeModSelector);

    /** Sets the bundle of translated messages, or null to use the messages from the Soy source. */
    @CanIgnoreReturnValue
    Renderer setMsgBundle(SoyMsgBundle msgBundle);

    /** Sets the ID renaming map. */
    @CanIgnoreReturnValue
    Renderer setIdRenamingMap(SoyIdRenamingMap idRenamingMap);

    /** Sets the CSS renaming map. */
    @CanIgnoreReturnValue
    Renderer setCssRenamingMap(SoyCssRenamingMap cssRenamingMap);

    /** Configures if we should render additional HTML comments for runtime inspection. */
    @CanIgnoreReturnValue
    Renderer setDebugSoyTemplateInfo(boolean debugSoyTemplateInfo);

    /**
     * Renders the configured html template to the given appendable.
     *
     * @throws IllegalArgumentException If the content kind is not {@link ContentKind.HTML}
     *     (corresponding to kind="html" in the template).
     * @throws SoyTofuException if an error occurs during rendering.
     */
    void renderHtml(Appendable out);

    /**
     * Renders the configured html template as a {@link SanitizedContent} of type {@link
     * ContentKind.HTML}.
     *
     * @throws IllegalArgumentException If the content kind is not {@link ContentKind.HTML}
     *     (corresponding to kind="html" in the template).
     * @throws SoyTofuException if an error occurs during rendering.
     */
    SanitizedContent renderHtml();

    /**
     * Renders the configured javascript template to the given appendable.
     *
     * @throws IllegalArgumentException If the content kind is not {@link ContentKind.JS}
     *     (corresponding to kind="js" in the template).
     * @throws SoyTofuException if an error occurs during rendering.
     */
    void renderJs(Appendable out);

    /**
     * Renders the configured javascript template as a {@link SanitizedContent} of type {@link
     * ContentKind.JS}.
     *
     * @throws IllegalArgumentException If the content kind is not {@link ContentKind.JS}
     *     (corresponding to kind="js" in the template).
     * @throws SoyTofuException if an error occurs during rendering.
     */
    SanitizedContent renderJs();

    /**
     * Renders the configured uri template to the given appendable.
     *
     * @throws IllegalArgumentException If the content kind is not {@link ContentKind.URI}
     *     (corresponding to kind="uri" in the template).
     * @throws SoyTofuException if an error occurs during rendering.
     */
    void renderUri(Appendable out);

    /**
     * Renders the configured uri template as a {@link SanitizedContent} of type {@link
     * ContentKind.URI}.
     *
     * @throws IllegalArgumentException If the content kind is not {@link ContentKind.URI}
     *     (corresponding to kind="uri" in the template).
     * @throws SoyTofuException if an error occurs during rendering.
     */
    SanitizedContent renderUri();

    /**
     * Renders the configured trusted resource uri template to the given appendable.
     *
     * @throws IllegalArgumentException If the content kind is not {@link
     *     ContentKind.TRUSTED_RESOURCE_URI} (corresponding to kind="trusted_resource_uri" in the
     *     template).
     * @throws SoyTofuException if an error occurs during rendering.
     */
    void renderTrustedResourceUri(Appendable out);

    /**
     * Renders the configured trusted resource uri template as a {@link SanitizedContent} of type
     * {@link ContentKind.TRUSTED_RESOURCE_URI}.
     *
     * @throws IllegalArgumentException If the content kind is not {@link
     *     ContentKind.TRUSTED_RESOURCE_URI} (corresponding to kind="trusted_resource_uri" in the
     *     template).
     * @throws SoyTofuException if an error occurs during rendering.
     */
    SanitizedContent renderTrustedResourceUri();

    /**
     * Renders the configured attributes template to the given appendable.
     *
     * @throws IllegalArgumentException If the content kind is not {@link ContentKind.ATTRIBUTES}
     *     (corresponding to kind="attributes" in the template).
     * @throws SoyTofuException if an error occurs during rendering.
     */
    void renderAttributes(Appendable out);

    /**
     * Renders the configured css template as a {@link SanitizedContent} of type {@link
     * ContentKind.ATTRIBUTES}.
     *
     * @throws IllegalArgumentException If the content kind is not {@link ContentKind.ATTRIBUTES}
     *     (corresponding to kind="attributes" in the template).
     * @throws SoyTofuException if an error occurs during rendering.
     */
    SanitizedContent renderAttributes();

    /**
     * Renders the configured css template to the given appendable.
     *
     * @throws IllegalArgumentException If the content kind is not {@link ContentKind.CSS}
     *     (corresponding to kind="css" in the template).
     * @throws SoyTofuException if an error occurs during rendering.
     */
    void renderCss(Appendable out);

    /**
     * Renders the configured css template as a {@link SanitizedContent} of type {@link
     * ContentKind.CSS}.
     *
     * @throws IllegalArgumentException If the content kind is not {@link ContentKind.CSS}
     *     (corresponding to kind="css" in the template).
     * @throws SoyTofuException if an error occurs during rendering.
     */
    SanitizedContent renderCss();

    /**
     * Renders the configured template to the given appendable. Any template type can be rendered as
     * text.
     *
     * @throws SoyTofuException if an error occurs during rendering.
     */
    void renderText(Appendable out);

    /**
     * Renders the configured template as a string. Any template type can be rendered as text.
     *
     * @throws SoyTofuException if an error occurs during rendering.
     */
    String renderText();

    // ---------------------------------------------------------------------------------------------
    // Old render methods.

    /**
     * Renders the template using the data, injected data, and message bundle previously set.
     *
     * <p>Checks the content kind of the template. kind="html" templates are allowed, unless
     * setContentKind was called. The goal is to prevent accidental rendering of unescaped
     * kind="text" in contexts where that could XSS.
     *
     * @throws SoyTofuException if an error occurs during rendering.
     * @deprecated For text content, use {@link #renderText()} directly. Otherwise, use {@link
     *     #renderHtml()}, {@link #renderCss()}, etc. to verify the content type, and then call
     *     toString() to convert to a string.
     */
    @Deprecated
    String render();

    /**
     * Renders the template using the data, injected data, and message bundle previously set into
     * the given Appendable.
     *
     * <p>Checks the content kind of the template. kind="html" templates are allowed, unless
     * setContentKind was called. The goal is to prevent accidental rendering of unescaped
     * kind="text" in contexts where that could XSS.
     *
     * @deprecated Use {@link #renderHtml(Appendable)}, {@link #renderCss(Appendable)}, etc.
     *     instead.
     * @throws SoyTofuException if an error occurs during rendering.
     */
    @Deprecated
    SanitizedContent.ContentKind render(Appendable out);

    /**
     * Renders the template as a SanitizedContent object, which can be used as an input to another
     * Soy template, or used to verify that the output type is correct.
     *
     * <p>This returns a SanitizedContent object corresponding to the kind="..." attribute of the
     * template. The expected content kind must be set beforehand, unless HTML is expected, to avoid
     * an exception.
     *
     * @deprecated Use {@link #renderHtml()}, {@link #renderCss()}, etc. instead.
     * @throws IllegalArgumentException If the kind doesn't match the expected kind (from
     *     setContentKind, or the default of HTML).
     * @throws SoyTofuException if an error occurs during rendering.
     */
    @Deprecated
    SanitizedContent renderStrict();
  }
}
