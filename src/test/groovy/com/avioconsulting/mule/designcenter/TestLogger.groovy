package com.avioconsulting.mule.designcenter

import org.apache.maven.plugin.logging.SystemStreamLog

class TestLogger extends SystemStreamLog {
    List<String> errors = []

    @Override
    void error(CharSequence content) {
        super.error(content)
        errors << content.toString()
    }

    @Override
    void error(CharSequence content, Throwable error) {
        super.error(content,
                    error)
        errors << content.toString()
    }

    @Override
    boolean isDebugEnabled() {
        true
    }
}
