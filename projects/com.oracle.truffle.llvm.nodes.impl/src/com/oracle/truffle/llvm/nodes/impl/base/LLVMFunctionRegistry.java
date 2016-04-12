/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.nodes.impl.base;

import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.parser.NodeFactoryFacade;
import com.oracle.truffle.llvm.runtime.LLVMOptimizationConfiguration;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor.LLVMRuntimeType;

/**
 * Manages Sulong functions and intrinsified native functions.
 */
public class LLVMFunctionRegistry {

    private final Map<String, NodeFactory<? extends LLVMNode>> intrinsics;
    private final NodeFactoryFacade facade;

    /**
     * The function index assigned to the next function descriptor
     */
    private int currentFunctionIndex;

    /**
     * Maps a function index (see {@link LLVMFunctionDescriptor#getFunctionIndex()} to a call
     * target.
     */
    @CompilationFinal private RootCallTarget[] functionPtrCallTargetMap;

    /**
     * Maps a function index (see {@link LLVMFunctionDescriptor#getFunctionIndex()} to a function
     * descriptor.
     */
    @CompilationFinal private LLVMFunctionDescriptor[] functionDescriptors = new LLVMFunctionDescriptor[0];

    public LLVMFunctionRegistry(LLVMOptimizationConfiguration optimizationConfig, NodeFactoryFacade facade) {
        this.facade = facade;
        this.intrinsics = facade.getFunctionSubstitutionFactories(optimizationConfig);
    }

    /**
     * Looks up the call target for a specific function. The lookup may return <code>null</code> if
     * the function is a native function or if the function cannot be found.
     *
     * @param function the function
     * @return the call target, <code>null</code> if not found.
     */
    public RootCallTarget lookup(LLVMFunctionDescriptor function) {
        int functionIndex = function.getFunctionIndex();
        if (functionIndex >= 0 && functionIndex < functionPtrCallTargetMap.length) {
            RootCallTarget result = functionPtrCallTargetMap[functionIndex];
            return result;
        } else {
            return null;
        }
    }

    public void register(Map<LLVMFunctionDescriptor, RootCallTarget> functionCallTargets) {
        CompilerAsserts.neverPartOfCompilation();
        functionPtrCallTargetMap = new RootCallTarget[functionDescriptors.length + intrinsics.size()];
        registerIntrinsics();
        for (LLVMFunctionDescriptor func : functionCallTargets.keySet()) {
            functionPtrCallTargetMap[func.getFunctionIndex()] = functionCallTargets.get(func);
        }
    }

    private void registerIntrinsics() {
        for (String intrinsicFunction : intrinsics.keySet()) {
            LLVMFunctionDescriptor function = createFunctionDescriptor(intrinsicFunction, LLVMRuntimeType.ILLEGAL, new LLVMRuntimeType[0], false);
            NodeFactory<? extends LLVMNode> nodeFactory = intrinsics.get(intrinsicFunction);
            List<Class<? extends Node>> executionSignature = nodeFactory.getExecutionSignature();
            int nrArguments = executionSignature.size();
            LLVMNode[] args = new LLVMNode[nrArguments];
            for (int i = 0; i < nrArguments; i++) {
                args[i] = facade.createFunctionArgNode(i, executionSignature.get(i));
            }
            LLVMNode intrinsicNode = nodeFactory.createNode((Object[]) args);
            RootNode functionRoot = facade.createFunctionSubstitutionRootNode(intrinsicNode);
            RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(functionRoot);
            assert functionPtrCallTargetMap[function.getFunctionIndex()] == null;
            functionPtrCallTargetMap[function.getFunctionIndex()] = callTarget;
        }
    }

    public LLVMFunctionDescriptor createFunctionDescriptor(String name, LLVMRuntimeType convertType, LLVMRuntimeType[] convertTypes, boolean varArgs) {
        CompilerAsserts.neverPartOfCompilation();
        for (int i = 0; i < functionDescriptors.length; i++) {
            if (functionDescriptors[i].getName().equals(name)) {
                return functionDescriptors[i];
            }
        }
        LLVMFunctionDescriptor function = LLVMFunctionDescriptor.create(name, convertType, convertTypes, varArgs, currentFunctionIndex++);
        LLVMFunctionDescriptor[] newFunctions = new LLVMFunctionDescriptor[functionDescriptors.length + 1];
        System.arraycopy(functionDescriptors, 0, newFunctions, 0, functionDescriptors.length);
        newFunctions[function.getFunctionIndex()] = function;
        functionDescriptors = newFunctions;
        return function;
    }

    public LLVMFunctionDescriptor createFromIndex(long addr) {
        LLVMFunctionDescriptor llvmFunction = functionDescriptors[(int) addr];
        assert llvmFunction != null;
        return llvmFunction;
    }

}
