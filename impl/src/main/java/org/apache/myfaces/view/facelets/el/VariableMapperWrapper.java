/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.myfaces.view.facelets.el;

import java.util.HashMap;
import java.util.Map;

import jakarta.el.ELException;
import jakarta.el.ValueExpression;
import jakarta.el.VariableMapper;
import jakarta.faces.FacesWrapper;

/**
 * Utility class for wrapping another VariableMapper with a new context, represented by a {@link java.util.Map Map}.
 * Modifications occur to the Map instance, but resolve against the wrapped VariableMapper if the Map doesn't contain
 * the ValueExpression requested.
 * 
 * @author Jacob Hookom
 * @version $Id$
 */
public final class VariableMapperWrapper extends VariableMapperBase implements FacesWrapper<VariableMapper>
{
    private final VariableMapper _target;
    private final VariableMapperBase _targetBase;
    private Map<String, ValueExpression> _vars;
    public boolean _trackResolveVariables;
    public boolean _variableResolved;

    public VariableMapperWrapper(VariableMapper orig)
    {
        super();
        _target = orig;
        _targetBase = (orig instanceof VariableMapperBase) ? (VariableMapperBase) orig : null;
        _trackResolveVariables = false;
        _variableResolved = false;
    }

    /**
     * First tries to resolve agains the inner Map, then the wrapped ValueExpression.
     * 
     * @see jakarta.el.VariableMapper#resolveVariable(java.lang.String)
     */
    @Override
    public ValueExpression resolveVariable(String variable)
    {
        ValueExpression ve = null;
        try
        {
            if (_vars != null)
            {
                ve = _vars.get(variable);

                // Is this code in a block that wants to cache 
                // the resulting expression(s) and variable has been resolved?
                if (_trackResolveVariables && ve != null)
                {
                    _variableResolved = true;
                }
            }
            
            if (ve == null)
            {
                return _target.resolveVariable(variable);
            }
            
            return ve;
        }
        catch (StackOverflowError e)
        {
            throw new ELException("Could not Resolve Variable [Overflow]: " + variable, e);
        }
    }

    /**
     * Set the ValueExpression on the inner Map instance.
     * 
     * @see jakarta.el.VariableMapper#setVariable(java.lang.String, jakarta.el.ValueExpression)
     */
    @Override
    public ValueExpression setVariable(String variable, ValueExpression expression)
    {
        if (_vars == null)
        {
            _vars = new HashMap<>();
        }
        
        return _vars.put(variable, expression);
    }

    @Override
    public boolean isAnyFaceletsVariableResolved()
    {
        if (_trackResolveVariables)
        {
            if (_variableResolved)
            {
                //Force EL creation!
                return true;
            }
            else
            {
                if (_targetBase != null)
                {
                    return _targetBase.isAnyFaceletsVariableResolved();
                }
                else
                {
                    // Another VariableMapper not extending from the base one was used. 
                    // (that's the reason why _targetBase is null).
                    // It is not possible to be sure the EL expression could use that mapper, 
                    // so return true to force EL expression creation.
                    return true;
                }
            }
        }
        else
        {
            // Force expression creation, because the call is outside caching block.
            return true;
        }
    }

    @Override
    public VariableMapper getWrapped()
    {
        return _target;
    }

    @Override
    public void beforeConstructELExpression()
    {
        _trackResolveVariables = true;
        _variableResolved = false;
        if (_targetBase != null)
        {
            _targetBase.beforeConstructELExpression();
        }
    }

    @Override
    public void afterConstructELExpression()
    {
        if (_targetBase != null)
        {
            _targetBase.afterConstructELExpression();
        }
        _trackResolveVariables = false;
        _variableResolved = false;
    }
}
