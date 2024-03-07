/*
 * $Id$
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.tb.common.struts;

import static org.springframework.web.context.support.WebApplicationContextUtils.getRequiredWebApplicationContext;

import lombok.SneakyThrows;
import org.apache.struts.action.Action;
import org.apache.struts.chain.contexts.ActionContext;
import org.apache.struts.chain.contexts.ServletActionContext;
import org.apache.struts.config.ActionConfig;
import org.springframework.util.ClassUtils;

public class CreateActionCommand extends org.apache.struts.chain.commands.AbstractCreateAction {

    @Override
    protected Action getAction(ActionContext context, String type, ActionConfig actionConfig) {
        Action action = this.getDelegateAction(context, actionConfig);
        if(action == null) {
            throw new RuntimeException("ouch! no bean found for action with id " + actionConfig.getActionId() + "(Path=" + actionConfig.getPath() + ")");
        }
        return action;
    }

    private Action getDelegateAction(ActionContext context, ActionConfig config) {
        Class<?> actionClassType = this.determineActionClass(config);
        if(context instanceof ServletActionContext servletActionContext) {
            return (Action) getRequiredWebApplicationContext(servletActionContext.getActionServlet().getServletContext()).getBean(actionClassType);
        }
        throw new IllegalStateException("Not a servlet request (ServletActionContext) - WTF? " + context.getClass());
    }

    @SneakyThrows
    private Class<?> determineActionClass(ActionConfig config) {
        if(config.getType() == null) {
            throw new RuntimeException("Missing type attribute in struts-config.xml action declaration for path " + config.getPath());
        }
        String actionClassName = config.getType();
        return ClassUtils.forName(actionClassName, Thread.currentThread().getContextClassLoader());
    }

}