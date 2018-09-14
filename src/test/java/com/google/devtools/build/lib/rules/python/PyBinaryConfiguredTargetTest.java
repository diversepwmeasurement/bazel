// Copyright 2018 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.rules.python;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import com.google.devtools.build.lib.analysis.configuredtargets.FileConfiguredTarget;
import com.google.devtools.build.lib.testutil.TestConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@code py_binary}. */
@RunWith(JUnit4.class)
public class PyBinaryConfiguredTargetTest extends PyExecutableConfiguredTargetTestBase {

  public PyBinaryConfiguredTargetTest() {
    super("py_binary");
  }

  @Test
  public void python2WithPy3Dependency() throws Exception {
    eventCollector.clear();
    reporter.removeHandler(failFastHandler); // expect errors
    scratch.file("python2/BUILD",
        "py_library(name = 'py3lib',",
        "    srcs = [],",
        "    srcs_version = 'PY3')",
        "py_binary(name = 'pybin',",
        "    srcs = ['pybin.py'],",
        "    deps = [':py3lib'],",
        "    srcs_version = 'PY2',",
        "    default_python_version = 'PY2')");
    assertThat(view.hasErrors(getConfiguredTarget("//python2:pybin"))).isTrue();
    assertContainsEvent("//python2:py3lib: Rule '//python2:py3lib' can only be used with Python 3");
  }

  @Test
  public void python2WithPy3OnlyDependency() throws Exception {
    eventCollector.clear();
    reporter.removeHandler(failFastHandler); // expect errors
    scratch.file("python2/BUILD",
        "py_library(",
        "    name = 'py3lib',",
        "    srcs = [],",
        "    srcs_version = 'PY3ONLY')",
        "py_binary(",
        "    name = 'pybin',",
        "    srcs = ['pybin.py'],",
        "    deps = [':py3lib'],",
        "    srcs_version = 'PY2',",
        "    default_python_version = 'PY2')");
    assertThat(view.hasErrors(getConfiguredTarget("//python2:pybin"))).isTrue();
    assertContainsEvent("//python2:py3lib: Rule '//python2:py3lib' can only be used with Python 3");
  }

  @Test
  public void python3WithPy2OnlyDependency() throws Exception {
    eventCollector.clear();
    reporter.removeHandler(failFastHandler); // expect errors
    scratch.file("python3/BUILD",
        "py_library(",
        "    name = 'py2lib',",
        "    srcs = [],",
        "    srcs_version = 'PY2ONLY')",
        "py_binary(",
        "    name = 'pybin',",
        "    srcs = ['pybin.py'],",
        "    deps = [':py2lib'],",
        "    srcs_version = 'PY3',",
        "    default_python_version = 'PY3')");
    assertThat(view.hasErrors(getConfiguredTarget("//python3:pybin"))).isTrue();
    assertContainsEvent("//python3:py2lib: Rule '//python3:py2lib' can only be used with Python 2");
  }

  @Test
  public void filesToBuild() throws Exception {
    scratch.file("pkg/BUILD",
        "py_binary(",
        "    name = 'foo',",
        "    srcs = ['foo.py'])");
    ConfiguredTarget target = getConfiguredTarget("//pkg:foo");
    FileConfiguredTarget srcFile = getFileConfiguredTarget("//pkg:foo.py");
    assertThat(getFilesToBuild(target))
        .containsExactly(getExecutable(target), srcFile.getArtifact());
    assertThat(getExecutable(target).getExecPath().getPathString())
        .containsMatch(TestConstants.PRODUCT_NAME + "-out/.*/bin/pkg/foo");
  }

  @Test
  public void srcsIsMandatory() throws Exception {
    // This case is somewhat dominated by the test that the default main must be in srcs, but the
    // error message is different here.
    checkError("pkg", "foo",
        // error:
        "missing value for mandatory attribute 'srcs'",
        // build file:
        "py_binary(",
        "    name = 'foo',",
        "    deps = [':bar'])",
        "py_library(",
        "    name = 'bar',",
        "    srcs = ['bar.py'])");
  }

  @Test
  public void defaultMainMustBeInSrcs() throws Exception {
    checkError("pkg", "app",
        // error:
        "corresponding default 'app.py' does not appear",
        // build file:
        "py_binary(",
        "    name = 'app',",
        "    srcs = ['foo.py', 'bar.py'])");
  }

  @Test
  public void explicitMain() throws Exception {
    scratch.file("pkg/BUILD",
        "py_binary(",
        "    name = 'foo',",
        "    main = 'foo.py',",
        "    srcs = ['foo.py', 'bar.py'])");
    getConfiguredTarget("//pkg:foo");  // should not fail
  }

  @Test
  public void explicitMainMustBeInSrcs() throws Exception {
    checkError("pkg", "foo",
        // error:
        "could not find 'foo.py'",
        // build file:
        "py_binary(",
        "    name = 'foo',",
        "    main = 'foo.py',",
        "    srcs = ['bar.py', 'baz.py'])");
  }

  @Test
  public void defaultMainCannotBeAmbiguous() throws Exception {
    scratch.file("pkg1/BUILD",
        "py_binary(",
        "    name = 'foo',",
        "    srcs = ['bar.py'])");
    checkError("pkg2", "bar",
        // error:
        "default main file name 'bar.py' matches multiple files.  Perhaps specify an explicit file "
            + "with 'main' attribute?  Matches were: 'pkg2/bar.py' and 'pkg1/bar.py'",
        // build file:
        "py_binary(",
        "    name = 'bar',",
        "    srcs = ['bar.py', '//pkg1:bar.py'])");
  }

  @Test
  public void explicitMainCannotBeAmbiguous() throws Exception {
    scratch.file("pkg1/BUILD",
        "py_binary(",
        "    name = 'foo',",
        "    srcs = ['bar.py'])");
    checkError("pkg2", "foo",
        // error:
        "file name 'bar.py' specified by 'main' attribute matches multiple files: e.g., "
            + "'pkg2/bar.py' and 'pkg1/bar.py'",
        // build file:
        "py_binary(",
        "    name = 'foo',",
        "    main = 'bar.py',",
        "    srcs = ['bar.py', '//pkg1:bar.py'])");
  }

  @Test
  public void nameCannotEndInPy() throws Exception {
    checkError("pkg", "foo.py",
        // error:
        "name must not end in '.py'",
        // build file:
        "py_binary(",
        "    name = 'foo.py',",
        "    srcs = ['bar.py'])");
  }

  @Test
  public void defaultMainCanBeGenerated() throws Exception {
    scratch.file("pkg/BUILD",
        "genrule(",
        "    name = 'gen_py',",
        "    cmd = 'touch $(location foo.py)',",
        "    outs = ['foo.py'])",
        "py_binary(",
        "    name = 'foo',",
        "    srcs = [':gen_py'])");
    getConfiguredTarget("//pkg:foo");  // should not fail
  }

  @Test
  public void defaultMainCanHaveMultiplePathSegments() throws Exception {
    // Regression test for crash caused by use of getChild on a multi-segment rule name.
    scratch.file("pkg/BUILD",
        "py_binary(",
        "    name = 'foo/bar',",
        "    srcs = ['foo/bar.py'])");
    getConfiguredTarget("//pkg:foo/bar");  // should not fail
  }

  // TODO(brandjon): Add tests for content of stub Python script (particularly for choosing python
  // 2 or 3).
}
