/**
 * Autogenerated by Thrift Compiler (0.10.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.facebook.buck.distributed.thrift;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
@javax.annotation.Generated(value = "Autogenerated by Thrift Compiler (0.10.0)")
public class BuildRuleFinishedEvent implements org.apache.thrift.TBase<BuildRuleFinishedEvent, BuildRuleFinishedEvent._Fields>, java.io.Serializable, Cloneable, Comparable<BuildRuleFinishedEvent> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("BuildRuleFinishedEvent");

  private static final org.apache.thrift.protocol.TField BUILD_TARGET_FIELD_DESC = new org.apache.thrift.protocol.TField("buildTarget", org.apache.thrift.protocol.TType.STRING, (short)1);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new BuildRuleFinishedEventStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new BuildRuleFinishedEventTupleSchemeFactory();

  public java.lang.String buildTarget; // optional

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    BUILD_TARGET((short)1, "buildTarget");

    private static final java.util.Map<java.lang.String, _Fields> byName = new java.util.HashMap<java.lang.String, _Fields>();

    static {
      for (_Fields field : java.util.EnumSet.allOf(_Fields.class)) {
        byName.put(field.getFieldName(), field);
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, or null if its not found.
     */
    public static _Fields findByThriftId(int fieldId) {
      switch(fieldId) {
        case 1: // BUILD_TARGET
          return BUILD_TARGET;
        default:
          return null;
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, throwing an exception
     * if it is not found.
     */
    public static _Fields findByThriftIdOrThrow(int fieldId) {
      _Fields fields = findByThriftId(fieldId);
      if (fields == null) throw new java.lang.IllegalArgumentException("Field " + fieldId + " doesn't exist!");
      return fields;
    }

    /**
     * Find the _Fields constant that matches name, or null if its not found.
     */
    public static _Fields findByName(java.lang.String name) {
      return byName.get(name);
    }

    private final short _thriftId;
    private final java.lang.String _fieldName;

    _Fields(short thriftId, java.lang.String fieldName) {
      _thriftId = thriftId;
      _fieldName = fieldName;
    }

    public short getThriftFieldId() {
      return _thriftId;
    }

    public java.lang.String getFieldName() {
      return _fieldName;
    }
  }

  // isset id assignments
  private static final _Fields optionals[] = {_Fields.BUILD_TARGET};
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.BUILD_TARGET, new org.apache.thrift.meta_data.FieldMetaData("buildTarget", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(BuildRuleFinishedEvent.class, metaDataMap);
  }

  public BuildRuleFinishedEvent() {
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public BuildRuleFinishedEvent(BuildRuleFinishedEvent other) {
    if (other.isSetBuildTarget()) {
      this.buildTarget = other.buildTarget;
    }
  }

  public BuildRuleFinishedEvent deepCopy() {
    return new BuildRuleFinishedEvent(this);
  }

  @Override
  public void clear() {
    this.buildTarget = null;
  }

  public java.lang.String getBuildTarget() {
    return this.buildTarget;
  }

  public BuildRuleFinishedEvent setBuildTarget(java.lang.String buildTarget) {
    this.buildTarget = buildTarget;
    return this;
  }

  public void unsetBuildTarget() {
    this.buildTarget = null;
  }

  /** Returns true if field buildTarget is set (has been assigned a value) and false otherwise */
  public boolean isSetBuildTarget() {
    return this.buildTarget != null;
  }

  public void setBuildTargetIsSet(boolean value) {
    if (!value) {
      this.buildTarget = null;
    }
  }

  public void setFieldValue(_Fields field, java.lang.Object value) {
    switch (field) {
    case BUILD_TARGET:
      if (value == null) {
        unsetBuildTarget();
      } else {
        setBuildTarget((java.lang.String)value);
      }
      break;

    }
  }

  public java.lang.Object getFieldValue(_Fields field) {
    switch (field) {
    case BUILD_TARGET:
      return getBuildTarget();

    }
    throw new java.lang.IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new java.lang.IllegalArgumentException();
    }

    switch (field) {
    case BUILD_TARGET:
      return isSetBuildTarget();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that == null)
      return false;
    if (that instanceof BuildRuleFinishedEvent)
      return this.equals((BuildRuleFinishedEvent)that);
    return false;
  }

  public boolean equals(BuildRuleFinishedEvent that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_buildTarget = true && this.isSetBuildTarget();
    boolean that_present_buildTarget = true && that.isSetBuildTarget();
    if (this_present_buildTarget || that_present_buildTarget) {
      if (!(this_present_buildTarget && that_present_buildTarget))
        return false;
      if (!this.buildTarget.equals(that.buildTarget))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + ((isSetBuildTarget()) ? 131071 : 524287);
    if (isSetBuildTarget())
      hashCode = hashCode * 8191 + buildTarget.hashCode();

    return hashCode;
  }

  @Override
  public int compareTo(BuildRuleFinishedEvent other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.valueOf(isSetBuildTarget()).compareTo(other.isSetBuildTarget());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetBuildTarget()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.buildTarget, other.buildTarget);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    return 0;
  }

  public _Fields fieldForId(int fieldId) {
    return _Fields.findByThriftId(fieldId);
  }

  public void read(org.apache.thrift.protocol.TProtocol iprot) throws org.apache.thrift.TException {
    scheme(iprot).read(iprot, this);
  }

  public void write(org.apache.thrift.protocol.TProtocol oprot) throws org.apache.thrift.TException {
    scheme(oprot).write(oprot, this);
  }

  @Override
  public java.lang.String toString() {
    java.lang.StringBuilder sb = new java.lang.StringBuilder("BuildRuleFinishedEvent(");
    boolean first = true;

    if (isSetBuildTarget()) {
      sb.append("buildTarget:");
      if (this.buildTarget == null) {
        sb.append("null");
      } else {
        sb.append(this.buildTarget);
      }
      first = false;
    }
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // check for sub-struct validity
  }

  private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
    try {
      write(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(out)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, java.lang.ClassNotFoundException {
    try {
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class BuildRuleFinishedEventStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public BuildRuleFinishedEventStandardScheme getScheme() {
      return new BuildRuleFinishedEventStandardScheme();
    }
  }

  private static class BuildRuleFinishedEventStandardScheme extends org.apache.thrift.scheme.StandardScheme<BuildRuleFinishedEvent> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, BuildRuleFinishedEvent struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // BUILD_TARGET
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.buildTarget = iprot.readString();
              struct.setBuildTargetIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          default:
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();

      // check for required fields of primitive type, which can't be checked in the validate method
      struct.validate();
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot, BuildRuleFinishedEvent struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.buildTarget != null) {
        if (struct.isSetBuildTarget()) {
          oprot.writeFieldBegin(BUILD_TARGET_FIELD_DESC);
          oprot.writeString(struct.buildTarget);
          oprot.writeFieldEnd();
        }
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class BuildRuleFinishedEventTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public BuildRuleFinishedEventTupleScheme getScheme() {
      return new BuildRuleFinishedEventTupleScheme();
    }
  }

  private static class BuildRuleFinishedEventTupleScheme extends org.apache.thrift.scheme.TupleScheme<BuildRuleFinishedEvent> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, BuildRuleFinishedEvent struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      java.util.BitSet optionals = new java.util.BitSet();
      if (struct.isSetBuildTarget()) {
        optionals.set(0);
      }
      oprot.writeBitSet(optionals, 1);
      if (struct.isSetBuildTarget()) {
        oprot.writeString(struct.buildTarget);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, BuildRuleFinishedEvent struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      java.util.BitSet incoming = iprot.readBitSet(1);
      if (incoming.get(0)) {
        struct.buildTarget = iprot.readString();
        struct.setBuildTargetIsSet(true);
      }
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

