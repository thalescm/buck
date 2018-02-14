/**
 * Autogenerated by Thrift Compiler (0.10.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.facebook.buck.distributed.thrift;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
@javax.annotation.Generated(value = "Autogenerated by Thrift Compiler (0.10.0)", date = "2018-02-01")
public class SetCoordinatorRequest implements org.apache.thrift.TBase<SetCoordinatorRequest, SetCoordinatorRequest._Fields>, java.io.Serializable, Cloneable, Comparable<SetCoordinatorRequest> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("SetCoordinatorRequest");

  private static final org.apache.thrift.protocol.TField STAMPEDE_ID_FIELD_DESC = new org.apache.thrift.protocol.TField("stampedeId", org.apache.thrift.protocol.TType.STRUCT, (short)1);
  private static final org.apache.thrift.protocol.TField COORDINATOR_HOSTNAME_FIELD_DESC = new org.apache.thrift.protocol.TField("coordinatorHostname", org.apache.thrift.protocol.TType.STRING, (short)2);
  private static final org.apache.thrift.protocol.TField COORDINATOR_PORT_FIELD_DESC = new org.apache.thrift.protocol.TField("coordinatorPort", org.apache.thrift.protocol.TType.I32, (short)3);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new SetCoordinatorRequestStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new SetCoordinatorRequestTupleSchemeFactory();

  public StampedeId stampedeId; // optional
  public java.lang.String coordinatorHostname; // optional
  public int coordinatorPort; // optional

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    STAMPEDE_ID((short)1, "stampedeId"),
    COORDINATOR_HOSTNAME((short)2, "coordinatorHostname"),
    COORDINATOR_PORT((short)3, "coordinatorPort");

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
        case 1: // STAMPEDE_ID
          return STAMPEDE_ID;
        case 2: // COORDINATOR_HOSTNAME
          return COORDINATOR_HOSTNAME;
        case 3: // COORDINATOR_PORT
          return COORDINATOR_PORT;
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
  private static final int __COORDINATORPORT_ISSET_ID = 0;
  private byte __isset_bitfield = 0;
  private static final _Fields optionals[] = {_Fields.STAMPEDE_ID,_Fields.COORDINATOR_HOSTNAME,_Fields.COORDINATOR_PORT};
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.STAMPEDE_ID, new org.apache.thrift.meta_data.FieldMetaData("stampedeId", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, StampedeId.class)));
    tmpMap.put(_Fields.COORDINATOR_HOSTNAME, new org.apache.thrift.meta_data.FieldMetaData("coordinatorHostname", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.COORDINATOR_PORT, new org.apache.thrift.meta_data.FieldMetaData("coordinatorPort", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I32)));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(SetCoordinatorRequest.class, metaDataMap);
  }

  public SetCoordinatorRequest() {
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public SetCoordinatorRequest(SetCoordinatorRequest other) {
    __isset_bitfield = other.__isset_bitfield;
    if (other.isSetStampedeId()) {
      this.stampedeId = new StampedeId(other.stampedeId);
    }
    if (other.isSetCoordinatorHostname()) {
      this.coordinatorHostname = other.coordinatorHostname;
    }
    this.coordinatorPort = other.coordinatorPort;
  }

  public SetCoordinatorRequest deepCopy() {
    return new SetCoordinatorRequest(this);
  }

  @Override
  public void clear() {
    this.stampedeId = null;
    this.coordinatorHostname = null;
    setCoordinatorPortIsSet(false);
    this.coordinatorPort = 0;
  }

  public StampedeId getStampedeId() {
    return this.stampedeId;
  }

  public SetCoordinatorRequest setStampedeId(StampedeId stampedeId) {
    this.stampedeId = stampedeId;
    return this;
  }

  public void unsetStampedeId() {
    this.stampedeId = null;
  }

  /** Returns true if field stampedeId is set (has been assigned a value) and false otherwise */
  public boolean isSetStampedeId() {
    return this.stampedeId != null;
  }

  public void setStampedeIdIsSet(boolean value) {
    if (!value) {
      this.stampedeId = null;
    }
  }

  public java.lang.String getCoordinatorHostname() {
    return this.coordinatorHostname;
  }

  public SetCoordinatorRequest setCoordinatorHostname(java.lang.String coordinatorHostname) {
    this.coordinatorHostname = coordinatorHostname;
    return this;
  }

  public void unsetCoordinatorHostname() {
    this.coordinatorHostname = null;
  }

  /** Returns true if field coordinatorHostname is set (has been assigned a value) and false otherwise */
  public boolean isSetCoordinatorHostname() {
    return this.coordinatorHostname != null;
  }

  public void setCoordinatorHostnameIsSet(boolean value) {
    if (!value) {
      this.coordinatorHostname = null;
    }
  }

  public int getCoordinatorPort() {
    return this.coordinatorPort;
  }

  public SetCoordinatorRequest setCoordinatorPort(int coordinatorPort) {
    this.coordinatorPort = coordinatorPort;
    setCoordinatorPortIsSet(true);
    return this;
  }

  public void unsetCoordinatorPort() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __COORDINATORPORT_ISSET_ID);
  }

  /** Returns true if field coordinatorPort is set (has been assigned a value) and false otherwise */
  public boolean isSetCoordinatorPort() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __COORDINATORPORT_ISSET_ID);
  }

  public void setCoordinatorPortIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __COORDINATORPORT_ISSET_ID, value);
  }

  public void setFieldValue(_Fields field, java.lang.Object value) {
    switch (field) {
    case STAMPEDE_ID:
      if (value == null) {
        unsetStampedeId();
      } else {
        setStampedeId((StampedeId)value);
      }
      break;

    case COORDINATOR_HOSTNAME:
      if (value == null) {
        unsetCoordinatorHostname();
      } else {
        setCoordinatorHostname((java.lang.String)value);
      }
      break;

    case COORDINATOR_PORT:
      if (value == null) {
        unsetCoordinatorPort();
      } else {
        setCoordinatorPort((java.lang.Integer)value);
      }
      break;

    }
  }

  public java.lang.Object getFieldValue(_Fields field) {
    switch (field) {
    case STAMPEDE_ID:
      return getStampedeId();

    case COORDINATOR_HOSTNAME:
      return getCoordinatorHostname();

    case COORDINATOR_PORT:
      return getCoordinatorPort();

    }
    throw new java.lang.IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new java.lang.IllegalArgumentException();
    }

    switch (field) {
    case STAMPEDE_ID:
      return isSetStampedeId();
    case COORDINATOR_HOSTNAME:
      return isSetCoordinatorHostname();
    case COORDINATOR_PORT:
      return isSetCoordinatorPort();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that == null)
      return false;
    if (that instanceof SetCoordinatorRequest)
      return this.equals((SetCoordinatorRequest)that);
    return false;
  }

  public boolean equals(SetCoordinatorRequest that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_stampedeId = true && this.isSetStampedeId();
    boolean that_present_stampedeId = true && that.isSetStampedeId();
    if (this_present_stampedeId || that_present_stampedeId) {
      if (!(this_present_stampedeId && that_present_stampedeId))
        return false;
      if (!this.stampedeId.equals(that.stampedeId))
        return false;
    }

    boolean this_present_coordinatorHostname = true && this.isSetCoordinatorHostname();
    boolean that_present_coordinatorHostname = true && that.isSetCoordinatorHostname();
    if (this_present_coordinatorHostname || that_present_coordinatorHostname) {
      if (!(this_present_coordinatorHostname && that_present_coordinatorHostname))
        return false;
      if (!this.coordinatorHostname.equals(that.coordinatorHostname))
        return false;
    }

    boolean this_present_coordinatorPort = true && this.isSetCoordinatorPort();
    boolean that_present_coordinatorPort = true && that.isSetCoordinatorPort();
    if (this_present_coordinatorPort || that_present_coordinatorPort) {
      if (!(this_present_coordinatorPort && that_present_coordinatorPort))
        return false;
      if (this.coordinatorPort != that.coordinatorPort)
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + ((isSetStampedeId()) ? 131071 : 524287);
    if (isSetStampedeId())
      hashCode = hashCode * 8191 + stampedeId.hashCode();

    hashCode = hashCode * 8191 + ((isSetCoordinatorHostname()) ? 131071 : 524287);
    if (isSetCoordinatorHostname())
      hashCode = hashCode * 8191 + coordinatorHostname.hashCode();

    hashCode = hashCode * 8191 + ((isSetCoordinatorPort()) ? 131071 : 524287);
    if (isSetCoordinatorPort())
      hashCode = hashCode * 8191 + coordinatorPort;

    return hashCode;
  }

  @Override
  public int compareTo(SetCoordinatorRequest other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.valueOf(isSetStampedeId()).compareTo(other.isSetStampedeId());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetStampedeId()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.stampedeId, other.stampedeId);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.valueOf(isSetCoordinatorHostname()).compareTo(other.isSetCoordinatorHostname());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetCoordinatorHostname()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.coordinatorHostname, other.coordinatorHostname);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.valueOf(isSetCoordinatorPort()).compareTo(other.isSetCoordinatorPort());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetCoordinatorPort()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.coordinatorPort, other.coordinatorPort);
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
    java.lang.StringBuilder sb = new java.lang.StringBuilder("SetCoordinatorRequest(");
    boolean first = true;

    if (isSetStampedeId()) {
      sb.append("stampedeId:");
      if (this.stampedeId == null) {
        sb.append("null");
      } else {
        sb.append(this.stampedeId);
      }
      first = false;
    }
    if (isSetCoordinatorHostname()) {
      if (!first) sb.append(", ");
      sb.append("coordinatorHostname:");
      if (this.coordinatorHostname == null) {
        sb.append("null");
      } else {
        sb.append(this.coordinatorHostname);
      }
      first = false;
    }
    if (isSetCoordinatorPort()) {
      if (!first) sb.append(", ");
      sb.append("coordinatorPort:");
      sb.append(this.coordinatorPort);
      first = false;
    }
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // check for sub-struct validity
    if (stampedeId != null) {
      stampedeId.validate();
    }
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
      // it doesn't seem like you should have to do this, but java serialization is wacky, and doesn't call the default constructor.
      __isset_bitfield = 0;
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class SetCoordinatorRequestStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public SetCoordinatorRequestStandardScheme getScheme() {
      return new SetCoordinatorRequestStandardScheme();
    }
  }

  private static class SetCoordinatorRequestStandardScheme extends org.apache.thrift.scheme.StandardScheme<SetCoordinatorRequest> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, SetCoordinatorRequest struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // STAMPEDE_ID
            if (schemeField.type == org.apache.thrift.protocol.TType.STRUCT) {
              struct.stampedeId = new StampedeId();
              struct.stampedeId.read(iprot);
              struct.setStampedeIdIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // COORDINATOR_HOSTNAME
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.coordinatorHostname = iprot.readString();
              struct.setCoordinatorHostnameIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 3: // COORDINATOR_PORT
            if (schemeField.type == org.apache.thrift.protocol.TType.I32) {
              struct.coordinatorPort = iprot.readI32();
              struct.setCoordinatorPortIsSet(true);
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

    public void write(org.apache.thrift.protocol.TProtocol oprot, SetCoordinatorRequest struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.stampedeId != null) {
        if (struct.isSetStampedeId()) {
          oprot.writeFieldBegin(STAMPEDE_ID_FIELD_DESC);
          struct.stampedeId.write(oprot);
          oprot.writeFieldEnd();
        }
      }
      if (struct.coordinatorHostname != null) {
        if (struct.isSetCoordinatorHostname()) {
          oprot.writeFieldBegin(COORDINATOR_HOSTNAME_FIELD_DESC);
          oprot.writeString(struct.coordinatorHostname);
          oprot.writeFieldEnd();
        }
      }
      if (struct.isSetCoordinatorPort()) {
        oprot.writeFieldBegin(COORDINATOR_PORT_FIELD_DESC);
        oprot.writeI32(struct.coordinatorPort);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class SetCoordinatorRequestTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public SetCoordinatorRequestTupleScheme getScheme() {
      return new SetCoordinatorRequestTupleScheme();
    }
  }

  private static class SetCoordinatorRequestTupleScheme extends org.apache.thrift.scheme.TupleScheme<SetCoordinatorRequest> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, SetCoordinatorRequest struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      java.util.BitSet optionals = new java.util.BitSet();
      if (struct.isSetStampedeId()) {
        optionals.set(0);
      }
      if (struct.isSetCoordinatorHostname()) {
        optionals.set(1);
      }
      if (struct.isSetCoordinatorPort()) {
        optionals.set(2);
      }
      oprot.writeBitSet(optionals, 3);
      if (struct.isSetStampedeId()) {
        struct.stampedeId.write(oprot);
      }
      if (struct.isSetCoordinatorHostname()) {
        oprot.writeString(struct.coordinatorHostname);
      }
      if (struct.isSetCoordinatorPort()) {
        oprot.writeI32(struct.coordinatorPort);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, SetCoordinatorRequest struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      java.util.BitSet incoming = iprot.readBitSet(3);
      if (incoming.get(0)) {
        struct.stampedeId = new StampedeId();
        struct.stampedeId.read(iprot);
        struct.setStampedeIdIsSet(true);
      }
      if (incoming.get(1)) {
        struct.coordinatorHostname = iprot.readString();
        struct.setCoordinatorHostnameIsSet(true);
      }
      if (incoming.get(2)) {
        struct.coordinatorPort = iprot.readI32();
        struct.setCoordinatorPortIsSet(true);
      }
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

