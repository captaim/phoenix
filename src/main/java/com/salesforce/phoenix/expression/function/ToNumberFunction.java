/*******************************************************************************
 * Copyright (c) 2013, Salesforce.com, Inc.
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 *     Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *     Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *     Neither the name of Salesforce.com nor the names of its contributors may 
 *     be used to endorse or promote products derived from this software without 
 *     specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE 
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL 
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER 
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE 
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package com.salesforce.phoenix.expression.function;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.ParsePosition;
import java.util.List;

import org.apache.hadoop.hbase.io.ImmutableBytesWritable;

import com.salesforce.phoenix.expression.Expression;
import com.salesforce.phoenix.expression.LiteralExpression;
import com.salesforce.phoenix.parse.FunctionParseNode.Argument;
import com.salesforce.phoenix.parse.FunctionParseNode.BuiltInFunction;
import com.salesforce.phoenix.schema.PDataType;
import com.salesforce.phoenix.schema.tuple.Tuple;

/**
 * 
 * Implementation of TO_NUMBER(&lt;string&gt;, [&lt;pattern-string&gt;]) built-in function.  The format for the optional
 * <code>pattern_string</code> param is specified in {@link DecimalFormat}.
 *
 * @author elevine
 * @since 0.1
 */
@BuiltInFunction(name=ToNumberFunction.NAME, args= {
        @Argument(allowedTypes={PDataType.VARCHAR}),
        @Argument(allowedTypes={PDataType.VARCHAR}, isConstant=true, defaultValue="null")} )
public class ToNumberFunction extends ScalarFunction {
    public static final String NAME = "TO_NUMBER";
    
    private String formatString = null;
    private DecimalFormat format = null;
    
    public ToNumberFunction() {}

    public ToNumberFunction(List<Expression> children) throws SQLException {
        super(children.subList(0, 1));
        if (children.size() > 1) {
            formatString = (String)((LiteralExpression)children.get(1)).getValue();
            if (formatString != null) {
                format = new DecimalFormat(formatString);
                format.setParseBigDecimal(true);
            }
        }
    }
    
    @Override
    public boolean evaluate(Tuple tuple, ImmutableBytesWritable ptr) {
        if (!getExpression().evaluate(tuple, ptr)) {
            return false;
        } else if (ptr.getLength() == 0) {
            return true;
        }

        PDataType type = getExpression().getDataType();
        String stringValue = (String)type.toObject(ptr);
        if (stringValue == null) {
        	return false;
        }
        stringValue = stringValue.trim();
        BigDecimal decimalValue;
        if (format == null) {
            decimalValue = (BigDecimal) getDataType().toObject(stringValue);
        } else {
            ParsePosition parsePosition = new ParsePosition(0);
            Number number = format.parse(stringValue, parsePosition);
            if (parsePosition.getErrorIndex() > -1) {
                return false;
            }
            
            if (number instanceof BigDecimal) { 
                // since we set DecimalFormat.setParseBigDecimal(true) we are guaranteeing result to be 
                // of type BigDecimal in most cases.  see java.text.DecimalFormat.parse() JavaDoc.
                decimalValue = (BigDecimal)number;
            } else {
                return false;
            }
        }
        byte[] byteValue = getDataType().toBytes(decimalValue);
        ptr.set(byteValue);
        return true;
    }

    @Override
    public PDataType getDataType() {
        return PDataType.DECIMAL;
    }

    @Override
    public boolean isNullable() {
        return getExpression().isNullable();
    }

    private Expression getExpression() {
        return children.get(0);
    }

    @Override
    public String getName() {
        return NAME;
    }
}
