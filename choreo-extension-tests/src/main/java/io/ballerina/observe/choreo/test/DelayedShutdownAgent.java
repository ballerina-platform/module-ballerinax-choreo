/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.ballerina.observe.choreo.test;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import java.io.PrintStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/**
 * Java Agent for delaying shutdown.
 *
 * This agent is required as the BServerInstance fails if the program exits before it can connect to
 * ballerina test utils java agent. Therefore, to test failure conditions, the shutdown needs to be delayed.
 */
public class DelayedShutdownAgent {
    private static final String TARGET_CLASS_NAME = "ballerinax.choreo.0.extension_init";
    private static final String TARGET_METHOD_NAME = "init";

    private static final PrintStream outStream = System.out;
    private static final PrintStream errStream = System.err;
    private static boolean shutdownHookAdded = false;

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        outStream.println("Initializing Choreo Extension delayed shutdown Java agent");
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                byte[] byteCode = classfileBuffer;
                if (TARGET_CLASS_NAME.replaceAll("\\.", "/").equals(className)) {
                    try {
                        CtClass schedulerClass = ClassPool.getDefault().get(TARGET_CLASS_NAME);
                        CtMethod startMethod = schedulerClass.getDeclaredMethod(TARGET_METHOD_NAME);
                        startMethod.insertBefore(
                                DelayedShutdownAgent.class.getCanonicalName() + ".addShutdownHook();");
                        byteCode = schedulerClass.toBytecode();
                        schedulerClass.detach();
                    } catch (Throwable t) {
                        errStream.println("Error injecting the delayed shutdown logic: " + t.getMessage());
                    }
                }
                return byteCode;
            }
        });
    }

    public static void addShutdownHook() {
        if (!shutdownHookAdded) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    outStream.println("Delaying shutdown");
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    errStream.println("Delaying shutdown interrupted: " + e.getMessage());
                }
            }));
            shutdownHookAdded = true;
        }
    }
}
