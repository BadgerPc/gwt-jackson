/*
 * Copyright 2013 Nicolas Morel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.nmorel.gwtjackson.rebind;

import java.io.PrintWriter;
import java.util.Map.Entry;

import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.ObjectIdGenerator;
import com.github.nmorel.gwtjackson.client.JsonDeserializer;
import com.github.nmorel.gwtjackson.client.JsonSerializer;
import com.github.nmorel.gwtjackson.client.deser.bean.AbstractBeanJsonDeserializer;
import com.github.nmorel.gwtjackson.client.deser.bean.AbstractIdentityDeserializationInfo;
import com.github.nmorel.gwtjackson.client.deser.bean.PropertyIdentityDeserializationInfo;
import com.github.nmorel.gwtjackson.client.deser.map.key.KeyDeserializer;
import com.github.nmorel.gwtjackson.client.ser.bean.AbstractBeanJsonSerializer;
import com.github.nmorel.gwtjackson.client.ser.bean.AbstractIdentitySerializationInfo;
import com.github.nmorel.gwtjackson.client.ser.bean.ObjectIdSerializer;
import com.github.nmorel.gwtjackson.client.ser.bean.PropertyIdentitySerializationInfo;
import com.github.nmorel.gwtjackson.client.ser.map.key.KeySerializer;
import com.github.nmorel.gwtjackson.rebind.PropertyInfo.AdditionalMethod;
import com.github.nmorel.gwtjackson.rebind.RebindConfiguration.MapperInstance;
import com.github.nmorel.gwtjackson.rebind.RebindConfiguration.MapperType;
import com.github.nmorel.gwtjackson.rebind.type.JDeserializerType;
import com.github.nmorel.gwtjackson.rebind.type.JSerializerType;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.TreeLogger.Type;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JArrayType;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JEnumType;
import com.google.gwt.core.ext.typeinfo.JParameterizedType;
import com.google.gwt.core.ext.typeinfo.JType;
import com.google.gwt.core.ext.typeinfo.JTypeParameter;
import com.google.gwt.thirdparty.guava.common.base.Optional;
import com.google.gwt.user.rebind.AbstractSourceCreator;
import com.google.gwt.user.rebind.ClassSourceFileComposerFactory;
import com.google.gwt.user.rebind.SourceWriter;
import com.google.gwt.user.rebind.StringSourceWriter;

import static com.github.nmorel.gwtjackson.rebind.CreatorUtils.QUOTED_FUNCTION;

/**
 * @author Nicolas Morel
 */
public abstract class AbstractCreator extends AbstractSourceCreator {

    public static final String JSON_DESERIALIZER_CLASS = "com.github.nmorel.gwtjackson.client.JsonDeserializer";

    public static final String JSON_SERIALIZER_CLASS = "com.github.nmorel.gwtjackson.client.JsonSerializer";

    public static final String JSON_READER_CLASS = "com.github.nmorel.gwtjackson.client.stream.JsonReader";

    public static final String JSON_DESERIALIZATION_CONTEXT_CLASS = "com.github.nmorel.gwtjackson.client.JsonDeserializationContext";

    public static final String JSON_SERIALIZATION_CONTEXT_CLASS = "com.github.nmorel.gwtjackson.client.JsonSerializationContext";

    public static final String ARRAY_CREATOR_CLASS = "com.github.nmorel.gwtjackson.client.deser.array.ArrayJsonDeserializer.ArrayCreator";

    protected static final String IDENTITY_DESERIALIZATION_INFO_CLASS = "com.github.nmorel.gwtjackson.client.deser.bean" + "" + "" +
        ".IdentityDeserializationInfo";

    protected static final String IDENTITY_SERIALIZATION_INFO_CLASS = "com.github.nmorel.gwtjackson.client.ser.bean" + "" + "" +
        ".IdentitySerializationInfo";

    protected static final String TYPE_PARAMETER_PREFIX = "p_";

    protected static final String TYPE_PARAMETER_DESERIALIZER_FIELD_NAME = "deserializer%d";

    protected static final String TYPE_PARAMETER_SERIALIZER_FIELD_NAME = "serializer%d";

    protected static final String ABSTRACT_BEAN_JSON_DESERIALIZER_CLASS = "com.github.nmorel.gwtjackson.client.deser.bean" + "" +
        ".AbstractBeanJsonDeserializer";

    protected static final String ABSTRACT_BEAN_JSON_SERIALIZER_CLASS = "com.github.nmorel.gwtjackson.client.ser.bean" + "" +
        ".AbstractBeanJsonSerializer";

    protected static final String TYPE_DESERIALIZATION_INFO_CLASS = "com.github.nmorel.gwtjackson.client.deser.bean" + "" +
        ".TypeDeserializationInfo";

    protected static final String TYPE_SERIALIZATION_INFO_CLASS = "com.github.nmorel.gwtjackson.client.ser.bean" + "" +
        ".TypeSerializationInfo";

    protected final TreeLogger logger;

    protected final GeneratorContext context;

    protected final RebindConfiguration configuration;

    protected final JacksonTypeOracle typeOracle;

    protected AbstractCreator( TreeLogger logger, GeneratorContext context, RebindConfiguration configuration,
                               JacksonTypeOracle typeOracle ) {
        this.logger = logger;
        this.context = context;
        this.configuration = configuration;
        this.typeOracle = typeOracle;
    }

    protected PrintWriter getPrintWriter( String packageName, String className ) {
        return context.tryCreate( logger, packageName, className );
    }

    protected SourceWriter getSourceWriter( PrintWriter printWriter, String packageName, String className, String superClass,
                                            String... interfaces ) {
        ClassSourceFileComposerFactory composer = new ClassSourceFileComposerFactory( packageName, className );
        if ( null != superClass ) {
            composer.setSuperclass( superClass );
        }
        for ( String interfaceName : interfaces ) {
            composer.addImplementedInterface( interfaceName );
        }
        return composer.createSourceWriter( context, printWriter );
    }

    protected String getQualifiedClassName( JType type ) {
        if ( null == type.isPrimitive() ) {
            return type.getParameterizedQualifiedSourceName();
        } else {
            return type.isPrimitive().getQualifiedBoxedSourceName();
        }
    }

    /**
     * Build the string that instantiate a {@link JsonSerializer} for the given type. If the type is a bean,
     * the implementation of {@link AbstractBeanJsonSerializer} will
     * be created.
     *
     * @param type type
     *
     * @return the code instantiating the {@link JsonSerializer}. Examples:
     *         <ul>
     *         <li>ctx.getIntegerSerializer()</li>
     *         <li>new org.PersonBeanJsonSerializer()</li>
     *         </ul>
     */
    protected JSerializerType getJsonSerializerFromType( JType type ) throws UnableToCompleteException {
        return getJsonSerializerFromType( type, null );
    }

    /**
     * Build the string that instantiate a {@link JsonSerializer} for the given type. If the type is a bean,
     * the implementation of {@link AbstractBeanJsonSerializer} will
     * be created.
     *
     * @param type type
     * @param propertyInfo additionnal info to gives to the serializer
     *
     * @return the code instantiating the {@link JsonSerializer}. Examples:
     *         <ul>
     *         <li>ctx.getIntegerSerializer()</li>
     *         <li>new org.PersonBeanJsonSerializer()</li>
     *         </ul>
     */
    protected JSerializerType getJsonSerializerFromType( JType type, PropertyInfo propertyInfo ) throws UnableToCompleteException {
        JSerializerType.Builder builder = JSerializerType.builder().type( type );

        if ( null != propertyInfo && propertyInfo.isRawValue() ) {
            return builder.instance( String.format( "ctx.<%s>getRawValueJsonSerializer()", type.getParameterizedQualifiedSourceName() ) )
                .build();
        }

        JTypeParameter typeParameter = type.isTypeParameter();
        if ( null != typeParameter ) {
            return builder.instance( String.format( TYPE_PARAMETER_SERIALIZER_FIELD_NAME, typeParameter.getOrdinal() ) ).build();
        }

        Optional<MapperInstance> configuredSerializer = configuration.getSerializer( type );
        if ( configuredSerializer.isPresent() ) {
            builder.mapperType( configuredSerializer.get().getMapperType() );
            if ( null != type.isParameterized() ) {

                JSerializerType[] parametersSerializer = new JSerializerType[type.isParameterized().getTypeArgs().length];
                String[] params = new String[type.isParameterized().getTypeArgs().length];

                for ( int i = 0; i < params.length; i++ ) {
                    JSerializerType parameterSerializerType;
                    if ( MapperType.KEY_SERIALIZER == configuredSerializer.get().getParameters()[i] ) {
                        parameterSerializerType = getKeySerializerFromType( type.isParameterized().getTypeArgs()[i] );
                    } else {
                        parameterSerializerType = getJsonSerializerFromType( type.isParameterized().getTypeArgs()[i], propertyInfo );
                    }
                    parametersSerializer[i] = parameterSerializerType;
                    params[i] = parameterSerializerType.getInstance();
                }
                builder.parameters( parametersSerializer );
                builder.instance( String.format( configuredSerializer.get().getInstanceCreation(), params ) );

            } else {
                builder.instance( configuredSerializer.get().getInstanceCreation() );
            }
            return builder.build();
        }

        if ( typeOracle.isObject( type ) ) {
            logger.log( Type.ERROR, "java.lang.Object is not a supported type" );
            throw new UnableToCompleteException();
        }

        JEnumType enumType = type.isEnum();
        if ( null != enumType ) {
            builder.mapperType( typeOracle.getEnumJsonSerializerType() );
            return builder.instance( String.format( "ctx.<%s>getEnumJsonSerializer()", enumType.getQualifiedSourceName() ) ).build();
        }

        JArrayType arrayType = type.isArray();
        if ( null != arrayType ) {
            builder.mapperType( typeOracle.getArrayJsonSerializerType() );
            JSerializerType parameterSerializerType = getJsonSerializerFromType( arrayType.getComponentType(), propertyInfo );
            builder.parameters( new JSerializerType[]{parameterSerializerType} );
            return builder.instance( String.format( "ctx.newArrayJsonSerializer(%s)", parameterSerializerType.getInstance() ) ).build();
        }

        JClassType classType = type.isClassOrInterface();
        JParameterizedType parameterizedType = type.isParameterized();
        if ( null != classType ) {
            // it's a bean
            JClassType baseClassType = classType;
            if ( null != parameterizedType ) {
                // it's a bean with generics, we create a serializer based on generic type
                baseClassType = typeOracle.findGenericType( parameterizedType );
            }
            BeanJsonSerializerCreator beanJsonSerializerCreator = new BeanJsonSerializerCreator( logger
                .branch( Type.DEBUG, "Creating serializer for " + baseClassType
                    .getQualifiedSourceName() ), context, configuration, typeOracle );
            BeanJsonMapperInfo info = beanJsonSerializerCreator.create( baseClassType );

            StringBuilder joinedTypeParameters = new StringBuilder();
            StringBuilder joinedTypeParameterSerializers = new StringBuilder();
            if ( null != parameterizedType ) {
                JSerializerType[] parametersSerializer = new JSerializerType[parameterizedType.getTypeArgs().length];
                joinedTypeParameters.append( '<' );
                for ( int i = 0; i < parameterizedType.getTypeArgs().length; i++ ) {
                    if ( i > 0 ) {
                        joinedTypeParameters.append( ", " );
                        joinedTypeParameterSerializers.append( ", " );
                    }
                    JClassType argType = parameterizedType.getTypeArgs()[i];
                    joinedTypeParameters.append( argType.getParameterizedQualifiedSourceName() );

                    JSerializerType parameterSerializerType = getJsonSerializerFromType( argType );
                    parametersSerializer[i] = parameterSerializerType;
                    joinedTypeParameterSerializers.append( parameterSerializerType.getInstance() );
                }
                joinedTypeParameters.append( '>' );
                builder.parameters( parametersSerializer );
            }

            String parameters = generateBeanJsonSerializerParameters( classType, propertyInfo );
            String sep = "";
            if ( joinedTypeParameterSerializers.length() > 0 && !parameters.isEmpty() ) {
                sep = ", ";
            }

            builder.mapperType( typeOracle.findMapperType( info.getQualifiedSerializerClassName() ) );
            builder.instance( String.format( "new %s%s(%s%s%s)", info.getQualifiedSerializerClassName(), joinedTypeParameters
                .toString(), joinedTypeParameterSerializers, sep, parameters ) );
            return builder.build();
        }

        logger.log( TreeLogger.Type.ERROR, "Type '" + type.getQualifiedSourceName() + "' is not supported" );
        throw new UnableToCompleteException();
    }

    private String generateBeanJsonSerializerParameters( JClassType type, PropertyInfo propertyInfo ) throws UnableToCompleteException {
        if ( null == propertyInfo || (!propertyInfo.getIdentityInfo().isPresent() && !propertyInfo.getTypeInfo().isPresent()) ) {
            return "";
        }

        StringSourceWriter sourceWriter = new StringSourceWriter();

        if ( propertyInfo.getIdentityInfo().isPresent() ) {
            generateIdentifierSerializationInfo( sourceWriter, type, propertyInfo.getIdentityInfo().get() );
        } else {
            sourceWriter.print( "null" );
        }

        sourceWriter.print( ", " );

        if ( propertyInfo.getTypeInfo().isPresent() ) {
            generateTypeInfo( sourceWriter, propertyInfo.getTypeInfo(), true );
        } else {
            sourceWriter.print( "null" );
        }

        return sourceWriter.toString();
    }

    protected void generateIdentifierSerializationInfo( SourceWriter source, JClassType type, BeanIdentityInfo identityInfo ) throws
        UnableToCompleteException {

        if ( identityInfo.isIdABeanProperty() ) {
            source.println( "new %s<%s>(%s, \"%s\")", PropertyIdentitySerializationInfo.class.getName(), type
                .getParameterizedQualifiedSourceName(), identityInfo.isAlwaysAsId(), identityInfo.getPropertyName() );
        } else {
            String qualifiedType = getQualifiedClassName( identityInfo.getType() );
            String identityPropertyClass = String.format( "%s<%s, %s>", AbstractIdentitySerializationInfo.class.getName(), type
                .getParameterizedQualifiedSourceName(), qualifiedType );

            source.println( "new %s(%s, \"%s\") {", identityPropertyClass, identityInfo.isAlwaysAsId(), identityInfo.getPropertyName() );
            source.indent();

            source.println( "@Override" );
            source
                .println( "protected %s<%s> newSerializer(%s ctx) {", JSON_SERIALIZER_CLASS, qualifiedType,
                    JSON_SERIALIZATION_CONTEXT_CLASS );
            source.indent();
            source.println( "return %s;", getJsonSerializerFromType( identityInfo.getType() ).getInstance() );
            source.outdent();
            source.println( "}" );
            source.println();

            Optional<AdditionalMethod> additionalMethod = Optional.absent();

            source.println( "@Override" );
            source.println( "public %s<%s> getObjectId(%s bean, %s ctx) {", ObjectIdSerializer.class.getName(), qualifiedType, type
                .getParameterizedQualifiedSourceName(), JSON_SERIALIZATION_CONTEXT_CLASS );
            source.indent();

            String generatorType = String.format( "%s<%s>", ObjectIdGenerator.class.getName(), qualifiedType );
            source.println( "%s generator = new %s().forScope(%s.class);", generatorType, identityInfo.getGenerator()
                .getCanonicalName(), identityInfo.getScope().getName() );
            source.println( "%s scopedGen = ctx.findObjectIdGenerator(generator);", generatorType );
            source.println( "if(null == scopedGen) {" );
            source.indent();
            source.println( "scopedGen = generator.newForSerialization(ctx);" );
            source.println( "ctx.addGenerator(scopedGen);" );
            source.outdent();
            source.println( "}" );
            source.println( "return new %s<%s>(scopedGen.generateId(bean), getSerializer(ctx));", ObjectIdSerializer.class
                .getName(), qualifiedType );

            source.outdent();
            source.println( "}" );

            if ( additionalMethod.isPresent() ) {
                source.println();
                additionalMethod.get().write( source );
            }

            source.outdent();
            source.print( "}" );
        }
    }

    /**
     * Build the string that instantiate a {@link KeySerializer} for the given type.
     *
     * @param type type
     *
     * @return the code instantiating the {@link KeySerializer}.
     */
    protected JSerializerType getKeySerializerFromType( JType type ) throws UnableToCompleteException {
        JSerializerType.Builder builder = JSerializerType.builder().type( type );

        Optional<MapperInstance> keySerializer = configuration.getKeySerializer( type );
        if ( keySerializer.isPresent() ) {
            builder.mapperType( keySerializer.get().getMapperType() );
            builder.instance( keySerializer.get().getInstanceCreation() );
            return builder.build();
        }

        JEnumType enumType = type.isEnum();
        if ( null != enumType ) {
            builder.mapperType( typeOracle.getEnumKeySerializerType() );
            builder.instance( String.format( "ctx.<%s>getEnumKeySerializer()", enumType.getQualifiedSourceName() ) );
            return builder.build();
        }

        logger.log( TreeLogger.Type.ERROR, "Type '" + type.getQualifiedSourceName() + "' is not supported as map's key" );
        throw new UnableToCompleteException();
    }

    /**
     * Build the string that instantiate a {@link JsonDeserializer} for the given type. If the type is a bean,
     * the implementation of {@link AbstractBeanJsonDeserializer} will
     * be created.
     *
     * @param type type
     *
     * @return the code instantiating the deserializer. Examples:
     *         <ul>
     *         <li>ctx.getIntegerDeserializer()</li>
     *         <li>new org .PersonBeanJsonDeserializer()</li>
     *         </ul>
     */
    protected JDeserializerType getJsonDeserializerFromType( JType type ) throws UnableToCompleteException {
        return getJsonDeserializerFromType( type, null );
    }

    /**
     * Build the string that instantiate a {@link JsonDeserializer} for the given type. If the type is a bean,
     * the implementation of {@link AbstractBeanJsonDeserializer} will
     * be created.
     *
     * @param type type
     * @param propertyInfo additionnal info to gives to the deserializer
     *
     * @return the code instantiating the deserializer. Examples:
     *         <ul>
     *         <li>ctx.getIntegerDeserializer()</li>
     *         <li>new org.PersonBeanJsonDeserializer()</li>
     *         </ul>
     */
    protected JDeserializerType getJsonDeserializerFromType( JType type, PropertyInfo propertyInfo ) throws UnableToCompleteException {
        JDeserializerType.Builder builder = JDeserializerType.builder().type( type );

        JTypeParameter typeParameter = type.isTypeParameter();
        if ( null != typeParameter ) {
            return builder.instance( String.format( TYPE_PARAMETER_DESERIALIZER_FIELD_NAME, typeParameter.getOrdinal() ) ).build();
        }

        Optional<MapperInstance> configuredDeserializer = configuration.getDeserializer( type );
        if ( configuredDeserializer.isPresent() ) {
            builder.mapperType( configuredDeserializer.get().getMapperType() );

            if ( null != type.isParameterized() ) {

                JDeserializerType[] parametersDeserializer = new JDeserializerType[type.isParameterized().getTypeArgs().length];
                String[] params = new String[type.isParameterized().getTypeArgs().length];

                for ( int i = 0; i < params.length; i++ ) {
                    JDeserializerType parameterDeserializerType;
                    if ( MapperType.KEY_DESERIALIZER == configuredDeserializer.get().getParameters()[i] ) {
                        parameterDeserializerType = getKeyDeserializerFromType( type.isParameterized().getTypeArgs()[i] );
                    } else {
                        parameterDeserializerType = getJsonDeserializerFromType( type.isParameterized().getTypeArgs()[i], propertyInfo );
                    }

                    parametersDeserializer[i] = parameterDeserializerType;
                    params[i] = parameterDeserializerType.getInstance();
                }
                builder.parameters( parametersDeserializer );
                builder.instance( String.format( configuredDeserializer.get().getInstanceCreation(), params ) );

            } else {
                builder.instance( configuredDeserializer.get().getInstanceCreation() );
            }
            return builder.build();
        }

        if ( typeOracle.isObject( type ) ) {
            logger.log( Type.ERROR, "java.lang.Object is not a supported type" );
            throw new UnableToCompleteException();
        }

        JEnumType enumType = type.isEnum();
        if ( null != enumType ) {
            builder.mapperType( typeOracle.getEnumJsonDeserializerType() );
            return builder.instance( "ctx.newEnumJsonDeserializer(" + enumType.getQualifiedSourceName() + ".class)" ).build();
        }

        JArrayType arrayType = type.isArray();
        if ( null != arrayType ) {
            String method = "ctx.newArrayJsonDeserializer(%s, %s)";
            String arrayCreator = "new " + ARRAY_CREATOR_CLASS + "<" + arrayType.getComponentType()
                .getParameterizedQualifiedSourceName() + ">(){\n" +
                "  @Override\n" +
                "  public " + arrayType.getParameterizedQualifiedSourceName() + " create( int length ) {\n" +
                "    return new " + arrayType.getComponentType().getParameterizedQualifiedSourceName() + "[length];\n" +
                "  }\n" +
                "}";

            builder.mapperType( typeOracle.getArrayJsonDeserializerType() );
            JDeserializerType parameterDeserializerType = getJsonDeserializerFromType( arrayType.getComponentType(), propertyInfo );
            builder.parameters( new JDeserializerType[]{parameterDeserializerType} );
            return builder.instance( String.format( method, parameterDeserializerType.getInstance(), arrayCreator ) ).build();
        }

        JParameterizedType parameterizedType = type.isParameterized();
        if ( null != parameterizedType ) {
            if ( typeOracle.isEnumSet( parameterizedType ) ) {

                JDeserializerType parameterDeserializerType = getJsonDeserializerFromType( parameterizedType
                    .getTypeArgs()[0], propertyInfo );

                builder.mapperType( typeOracle.getEnumSetJsonDeserializerType() );
                builder.parameters( new JDeserializerType[]{parameterDeserializerType} );
                // EnumSet needs the enum class as parameter
                builder.instance( String.format( "ctx.newEnumSetJsonDeserializer(%s.class, %s)", parameterizedType.getTypeArgs()[0]
                    .getQualifiedSourceName(), parameterDeserializerType.getInstance() ) );

                return builder.build();

            }

            if ( typeOracle.isEnumMap( parameterizedType ) ) {

                JDeserializerType keyDeserializerType = getKeyDeserializerFromType( parameterizedType.getTypeArgs()[0] );
                JDeserializerType valueDeserializerType = getJsonDeserializerFromType( parameterizedType.getTypeArgs()[1], propertyInfo );

                builder.mapperType( typeOracle.getEnumMapJsonDeserializerType() );
                builder.parameters( new JDeserializerType[]{keyDeserializerType, valueDeserializerType} );
                // EnumMap needs the enum class as parameter
                builder.instance( String.format( "ctx.newEnumMapJsonDeserializer(%s.class, %s, %s)", parameterizedType.getTypeArgs()[0]
                    .getQualifiedSourceName(), keyDeserializerType.getInstance(), valueDeserializerType.getInstance() ) );

                return builder.build();
            }

            // other parameterized types are considered to be beans
        }

        JClassType classType = type.isClassOrInterface();
        if ( null != classType ) {
            // it's a bean
            JClassType baseClassType = classType;
            if ( null != parameterizedType ) {
                // it's a bean with generics, we create a deserializer based on generic type
                baseClassType = typeOracle.findGenericType( parameterizedType );
            }
            BeanJsonDeserializerCreator beanJsonDeserializerCreator = new BeanJsonDeserializerCreator( logger
                .branch( Type.DEBUG, "Creating deserializer for " + baseClassType
                    .getQualifiedSourceName() ), context, configuration, typeOracle );
            BeanJsonMapperInfo info = beanJsonDeserializerCreator.create( baseClassType );

            StringBuilder joinedTypeParameters = new StringBuilder();
            StringBuilder joinedTypeParameterDeserializers = new StringBuilder();
            if ( null != parameterizedType ) {
                JDeserializerType[] parametersDeserializer = new JDeserializerType[parameterizedType.getTypeArgs().length];
                joinedTypeParameters.append( '<' );
                for ( int i = 0; i < parameterizedType.getTypeArgs().length; i++ ) {
                    if ( i > 0 ) {
                        joinedTypeParameters.append( ", " );
                        joinedTypeParameterDeserializers.append( ", " );
                    }
                    JClassType argType = parameterizedType.getTypeArgs()[i];
                    joinedTypeParameters.append( argType.getParameterizedQualifiedSourceName() );

                    JDeserializerType parameterDeserializerType = getJsonDeserializerFromType( argType );
                    parametersDeserializer[i] = parameterDeserializerType;
                    joinedTypeParameterDeserializers.append( parameterDeserializerType.getInstance() );
                }
                joinedTypeParameters.append( '>' );
                builder.parameters( parametersDeserializer );
            }

            String parameters = generateBeanJsonDeserializerParameters( info.getType(), propertyInfo );
            String sep = "";
            if ( joinedTypeParameterDeserializers.length() > 0 && !parameters.isEmpty() ) {
                sep = ", ";
            }

            builder.mapperType( typeOracle.findMapperType( info.getQualifiedSerializerClassName() ) );
            builder.instance( String.format( "new %s%s(%s%s%s)", info
                .getQualifiedDeserializerClassName(), joinedTypeParameters, joinedTypeParameterDeserializers, sep,
                generateBeanJsonDeserializerParameters( info
                .getType(), propertyInfo ) ) );
            return builder.build();
        }

        logger.log( TreeLogger.Type.ERROR, "Type '" + type.getQualifiedSourceName() + "' is not supported" );
        throw new UnableToCompleteException();
    }

    private String generateBeanJsonDeserializerParameters( JClassType type, PropertyInfo propertyInfo ) throws UnableToCompleteException {
        if ( null == propertyInfo || (!propertyInfo.getIdentityInfo().isPresent() && !propertyInfo.getTypeInfo().isPresent()) ) {
            return "";
        }

        StringSourceWriter sourceWriter = new StringSourceWriter();

        if ( propertyInfo.getIdentityInfo().isPresent() ) {
            generateIdentifierDeserializationInfo( sourceWriter, type, propertyInfo.getIdentityInfo().get() );
        } else {
            sourceWriter.print( "null" );
        }

        sourceWriter.print( ", " );

        if ( propertyInfo.getTypeInfo().isPresent() ) {
            generateTypeInfo( sourceWriter, propertyInfo.getTypeInfo(), false );
        } else {
            sourceWriter.print( "null" );
        }

        return sourceWriter.toString();
    }

    protected void generateIdentifierDeserializationInfo( SourceWriter source, JClassType type, BeanIdentityInfo identityInfo ) throws
        UnableToCompleteException {
        if ( identityInfo.isIdABeanProperty() ) {

            source.println( "new %s<%s>(\"%s\", %s.class, %s.class)", PropertyIdentityDeserializationInfo.class.getName(), type
                .getParameterizedQualifiedSourceName(), identityInfo.getPropertyName(), identityInfo.getGenerator()
                .getCanonicalName(), identityInfo.getScope().getCanonicalName() );

        } else {

            String qualifiedType = getQualifiedClassName( identityInfo.getType() );

            String identityPropertyClass = String.format( "%s<%s, %s>", AbstractIdentityDeserializationInfo.class.getName(), type
                .getParameterizedQualifiedSourceName(), qualifiedType );

            source.println( "new %s(\"%s\", %s.class, %s.class) {", identityPropertyClass, identityInfo.getPropertyName(), identityInfo
                .getGenerator().getCanonicalName(), identityInfo.getScope().getCanonicalName() );
            source.indent();

            source.println( "@Override" );
            source
                .println( "protected %s<%s> newDeserializer(%s ctx) {", JSON_DESERIALIZER_CLASS, qualifiedType,
                    JSON_DESERIALIZATION_CONTEXT_CLASS );
            source.indent();
            source.println( "return %s;", getJsonDeserializerFromType( identityInfo.getType() ).getInstance() );
            source.outdent();
            source.println( "}" );
            source.println();

            source.outdent();
            source.print( "}" );
        }
    }

    /**
     * Build the string that instantiate a {@link KeyDeserializer} for the given type.
     *
     * @param type type
     *
     * @return the code instantiating the {@link KeyDeserializer}.
     */
    protected JDeserializerType getKeyDeserializerFromType( JType type ) throws UnableToCompleteException {
        JDeserializerType.Builder builder = JDeserializerType.builder();
        builder.type( type );

        Optional<MapperInstance> keyDeserializer = configuration.getKeyDeserializer( type );
        if ( keyDeserializer.isPresent() ) {
            builder.mapperType( keyDeserializer.get().getMapperType() );
            builder.instance( keyDeserializer.get().getInstanceCreation() );
            return builder.build();
        }

        JEnumType enumType = type.isEnum();
        if ( null != enumType ) {
            builder.mapperType( typeOracle.getEnumKeyDeserializerType() );
            builder.instance( String.format( "ctx.newEnumKeyDeserializer(%s.class)", enumType.getQualifiedSourceName() ) );
            return builder.build();
        }

        logger.log( TreeLogger.Type.ERROR, "Type '" + type.getQualifiedSourceName() + "' is not supported as map's key" );
        throw new UnableToCompleteException();
    }

    protected void generateTypeInfo( SourceWriter source, Optional<BeanTypeInfo> typeInfo,
                                     boolean serialization ) throws UnableToCompleteException {
        String typeInfoProperty = null;
        if ( null != typeInfo.get().getPropertyName() ) {
            typeInfoProperty = QUOTED_FUNCTION.apply( typeInfo.get().getPropertyName() );
        }
        source.println( "new %s(%s.%s, %s)", serialization ? TYPE_SERIALIZATION_INFO_CLASS : TYPE_DESERIALIZATION_INFO_CLASS, As.class
            .getCanonicalName(), typeInfo.get().getInclude(), typeInfoProperty );
        source.indent();

        for ( Entry<JClassType, String> entry : typeInfo.get().getMapTypeToMetadata().entrySet() ) {
            source.println( ".addTypeInfo(%s.class, \"%s\")", entry.getKey().getQualifiedSourceName(), entry.getValue() );
        }

        source.outdent();
    }

}