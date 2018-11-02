/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.nativeplatform.fixtures.app

import static org.gradle.nativeplatform.fixtures.app.SourceFileElement.ofFile

class CppGreeterUsingCoreFoundation extends CppLibraryElement implements GreeterElement {
    final SourceFileElement header
    final SourceFileElement privateHeader
    final SourceFileElement source

    SourceElement getPublicHeaders() {
        return header
    }

    SourceElement getPrivateHeaders() {
        return privateHeader
    }

    SourceElement getSources() {
        return source
    }

    CppGreeterUsingCoreFoundation(String publicHeaderDir = "headers") {
        header = ofFile(sourceFile(publicHeaderDir, "greeter.h", """
#ifdef _WIN32
#define EXPORT_FUNC __declspec(dllexport)
#else
#define EXPORT_FUNC
#endif

class Greeter {
public:
    void EXPORT_FUNC sayHello();
};
"""))

        privateHeader = ofFile(sourceFile("headers", "greeter_consts.h", """
#define GREETING "${HelloWorldApp.HELLO_WORLD}"
"""))

        source = ofFile(sourceFile("cpp", "greeter.cpp", """
#include <CoreFoundation/CoreFoundation.h>
#include "greeter.h"
#include "greeter_consts.h"

void Greeter::sayHello() {
    CFShow(CFSTR(GREETING));
}
"""))
    }

    @Override
    CppGreeterUsingCoreFoundation asLib() {
        return new CppGreeterUsingCoreFoundation("public")
    }

    @Override
    String getExpectedOutput() {
        return "${HelloWorldApp.HELLO_WORLD}\n"
    }
}
