package org.graalvm.tools.lsp.server.request;

import org.graalvm.tools.lsp.definitions.ExampleDefinition;

import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;

public class BabylonianEventNodeFactory implements ExecutionEventNodeFactory {
    private final Env env;
    private final ExampleDefinition example;

    public BabylonianEventNodeFactory(Env env, ExampleDefinition example) {
        this.env = env;
        this.example = example;
    }

    public ExecutionEventNode create(EventContext context) {
        return new BabylonianEventNode(env, example, context);
    }
}
