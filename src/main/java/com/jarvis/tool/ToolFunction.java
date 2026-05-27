package com.jarvis.tool;

import java.util.Map;

public interface ToolFunction {
    String name();
    String description();
    Map<String, Object> parameters();
    String execute(Map<String, Object> args);
}
