// Copyright 2016 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.android;

import com.android.resources.ResourceType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.devtools.build.android.AndroidFrameworkAttrIdProvider.AttrLookupException;
import com.google.devtools.build.android.resources.FieldInitializers;
import com.google.devtools.build.android.resources.RClassGenerator;
import com.google.devtools.build.android.resources.RSourceGenerator;
import java.io.Flushable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Generates the R class for an android_library with made up field initializers for the ids. The
 * real ids will be assigned when we build the android_binary.
 *
 * <p>Collects the R class fields from the merged resource maps, and then writes out the resource
 * class files.
 */
public class AndroidResourceClassWriter implements Flushable, AndroidResourceSymbolSink {

  /** Create a new class writer. */
  public static AndroidResourceClassWriter createWith(
      Path androidJar, Path out, String javaPackage) {
    return of(new AndroidFrameworkAttrIdJar(androidJar), out, javaPackage);
  }

  @VisibleForTesting
  public static AndroidResourceClassWriter of(
      AndroidFrameworkAttrIdProvider androidIdProvider, Path outputBasePath, String packageName) {
    return new AndroidResourceClassWriter(
        PlaceholderIdFieldInitializerBuilder.from(androidIdProvider), outputBasePath, packageName);
  }

  private final Path outputBasePath;
  private final String packageName;
  private boolean includeClassFile = true;
  private boolean includeJavaFile = true;

  private final PlaceholderIdFieldInitializerBuilder generator;

  private AndroidResourceClassWriter(
      PlaceholderIdFieldInitializerBuilder generator, Path outputBasePath, String packageName) {
    this.generator = generator;
    this.outputBasePath = outputBasePath;
    this.packageName = packageName;
  }

  public void setIncludeClassFile(boolean include) {
    this.includeClassFile = include;
  }

  public void setIncludeJavaFile(boolean include) {
    this.includeJavaFile = include;
  }

  @Override
  public void acceptSimpleResource(ResourceType type, String name) {
    generator.addSimpleResource(type, name);
  }

  @Override
  public void acceptPublicResource(ResourceType type, String name, Optional<Integer> value) {
    generator.addPublicResource(type, name, value);
  }

  @Override
  public void acceptStyleableResource(
      FullyQualifiedName key, Map<FullyQualifiedName, Boolean> attrs) {
    generator.addStyleableResource(key, attrs);
  }

  @Override
  public void flush() throws IOException {
    try {
      FieldInitializers initializers = generator.build();
      if (includeClassFile) {
        writeAsClass(initializers);
      }
      if (includeJavaFile) {
        writeAsJava(initializers);
      }
    } catch (AttrLookupException e) {
      throw new IOException(e);
    }
  }

  private void writeAsJava(FieldInitializers initializers) throws IOException {
    RSourceGenerator.with(outputBasePath, initializers, false /* finalFields */).write(packageName);
  }

  private void writeAsClass(FieldInitializers initializers) throws IOException {
    RClassGenerator.with(outputBasePath, initializers, false /* finalFields */).write(packageName);
  }
}
