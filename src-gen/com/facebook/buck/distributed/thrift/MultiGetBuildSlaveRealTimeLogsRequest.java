/**
 * Autogenerated by Thrift Compiler (0.10.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.facebook.buck.distributed.thrift;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
@javax.annotation.Generated(value = "Autogenerated by Thrift Compiler (0.10.0)", date = "2018-02-01")
public class MultiGetBuildSlaveRealTimeLogsRequest implements org.apache.thrift.TBase<MultiGetBuildSlaveRealTimeLogsRequest, MultiGetBuildSlaveRealTimeLogsRequest._Fields>, java.io.Serializable, Cloneable, Comparable<MultiGetBuildSlaveRealTimeLogsRequest> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("MultiGetBuildSlaveRealTimeLogsRequest");

  private static final org.apache.thrift.protocol.TField STAMPEDE_ID_FIELD_DESC = new org.apache.thrift.protocol.TField("stampedeId", org.apache.thrift.protocol.TType.STRUCT, (short)1);
  private static final org.apache.thrift.protocol.TField BATCHES_FIELD_DESC = new org.apache.thrift.protocol.TField("batches", org.apache.thrift.protocol.TType.LIST, (short)2);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new MultiGetBuildSlaveRealTimeLogsRequestStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new MultiGetBuildSlaveRealTimeLogsRequestTupleSchemeFactory();

  public StampedeId stampedeId; // optional
  public java.util.List<LogLineBatchRequest> batches; // optional

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    STAMPEDE_ID((short)1, "stampedeId"),
    BATCHES((short)2, "batches");

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
        case 2: // BATCHES
          return BATCHES;
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
  private static final _Fields optionals[] = {_Fields.STAMPEDE_ID,_Fields.BATCHES};
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.STAMPEDE_ID, new org.apache.thrift.meta_data.FieldMetaData("stampedeId", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, StampedeId.class)));
    tmpMap.put(_Fields.BATCHES, new org.apache.thrift.meta_data.FieldMetaData("batches", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.ListMetaData(org.apache.thrift.protocol.TType.LIST, 
            new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, LogLineBatchRequest.class))));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(MultiGetBuildSlaveRealTimeLogsRequest.class, metaDataMap);
  }

  public MultiGetBuildSlaveRealTimeLogsRequest() {
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public MultiGetBuildSlaveRealTimeLogsRequest(MultiGetBuildSlaveRealTimeLogsRequest other) {
    if (other.isSetStampedeId()) {
      this.stampedeId = new StampedeId(other.stampedeId);
    }
    if (other.isSetBatches()) {
      java.util.List<LogLineBatchRequest> __this__batches = new java.util.ArrayList<LogLineBatchRequest>(other.batches.size());
      for (LogLineBatchRequest other_element : other.batches) {
        __this__batches.add(new LogLineBatchRequest(other_element));
      }
      this.batches = __this__batches;
    }
  }

  public MultiGetBuildSlaveRealTimeLogsRequest deepCopy() {
    return new MultiGetBuildSlaveRealTimeLogsRequest(this);
  }

  @Override
  public void clear() {
    this.stampedeId = null;
    this.batches = null;
  }

  public StampedeId getStampedeId() {
    return this.stampedeId;
  }

  public MultiGetBuildSlaveRealTimeLogsRequest setStampedeId(StampedeId stampedeId) {
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

  public int getBatchesSize() {
    return (this.batches == null) ? 0 : this.batches.size();
  }

  public java.util.Iterator<LogLineBatchRequest> getBatchesIterator() {
    return (this.batches == null) ? null : this.batches.iterator();
  }

  public void addToBatches(LogLineBatchRequest elem) {
    if (this.batches == null) {
      this.batches = new java.util.ArrayList<LogLineBatchRequest>();
    }
    this.batches.add(elem);
  }

  public java.util.List<LogLineBatchRequest> getBatches() {
    return this.batches;
  }

  public MultiGetBuildSlaveRealTimeLogsRequest setBatches(java.util.List<LogLineBatchRequest> batches) {
    this.batches = batches;
    return this;
  }

  public void unsetBatches() {
    this.batches = null;
  }

  /** Returns true if field batches is set (has been assigned a value) and false otherwise */
  public boolean isSetBatches() {
    return this.batches != null;
  }

  public void setBatchesIsSet(boolean value) {
    if (!value) {
      this.batches = null;
    }
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

    case BATCHES:
      if (value == null) {
        unsetBatches();
      } else {
        setBatches((java.util.List<LogLineBatchRequest>)value);
      }
      break;

    }
  }

  public java.lang.Object getFieldValue(_Fields field) {
    switch (field) {
    case STAMPEDE_ID:
      return getStampedeId();

    case BATCHES:
      return getBatches();

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
    case BATCHES:
      return isSetBatches();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that == null)
      return false;
    if (that instanceof MultiGetBuildSlaveRealTimeLogsRequest)
      return this.equals((MultiGetBuildSlaveRealTimeLogsRequest)that);
    return false;
  }

  public boolean equals(MultiGetBuildSlaveRealTimeLogsRequest that) {
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

    boolean this_present_batches = true && this.isSetBatches();
    boolean that_present_batches = true && that.isSetBatches();
    if (this_present_batches || that_present_batches) {
      if (!(this_present_batches && that_present_batches))
        return false;
      if (!this.batches.equals(that.batches))
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

    hashCode = hashCode * 8191 + ((isSetBatches()) ? 131071 : 524287);
    if (isSetBatches())
      hashCode = hashCode * 8191 + batches.hashCode();

    return hashCode;
  }

  @Override
  public int compareTo(MultiGetBuildSlaveRealTimeLogsRequest other) {
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
    lastComparison = java.lang.Boolean.valueOf(isSetBatches()).compareTo(other.isSetBatches());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetBatches()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.batches, other.batches);
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
    java.lang.StringBuilder sb = new java.lang.StringBuilder("MultiGetBuildSlaveRealTimeLogsRequest(");
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
    if (isSetBatches()) {
      if (!first) sb.append(", ");
      sb.append("batches:");
      if (this.batches == null) {
        sb.append("null");
      } else {
        sb.append(this.batches);
      }
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
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class MultiGetBuildSlaveRealTimeLogsRequestStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public MultiGetBuildSlaveRealTimeLogsRequestStandardScheme getScheme() {
      return new MultiGetBuildSlaveRealTimeLogsRequestStandardScheme();
    }
  }

  private static class MultiGetBuildSlaveRealTimeLogsRequestStandardScheme extends org.apache.thrift.scheme.StandardScheme<MultiGetBuildSlaveRealTimeLogsRequest> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, MultiGetBuildSlaveRealTimeLogsRequest struct) throws org.apache.thrift.TException {
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
          case 2: // BATCHES
            if (schemeField.type == org.apache.thrift.protocol.TType.LIST) {
              {
                org.apache.thrift.protocol.TList _list120 = iprot.readListBegin();
                struct.batches = new java.util.ArrayList<LogLineBatchRequest>(_list120.size);
                LogLineBatchRequest _elem121;
                for (int _i122 = 0; _i122 < _list120.size; ++_i122)
                {
                  _elem121 = new LogLineBatchRequest();
                  _elem121.read(iprot);
                  struct.batches.add(_elem121);
                }
                iprot.readListEnd();
              }
              struct.setBatchesIsSet(true);
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

    public void write(org.apache.thrift.protocol.TProtocol oprot, MultiGetBuildSlaveRealTimeLogsRequest struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.stampedeId != null) {
        if (struct.isSetStampedeId()) {
          oprot.writeFieldBegin(STAMPEDE_ID_FIELD_DESC);
          struct.stampedeId.write(oprot);
          oprot.writeFieldEnd();
        }
      }
      if (struct.batches != null) {
        if (struct.isSetBatches()) {
          oprot.writeFieldBegin(BATCHES_FIELD_DESC);
          {
            oprot.writeListBegin(new org.apache.thrift.protocol.TList(org.apache.thrift.protocol.TType.STRUCT, struct.batches.size()));
            for (LogLineBatchRequest _iter123 : struct.batches)
            {
              _iter123.write(oprot);
            }
            oprot.writeListEnd();
          }
          oprot.writeFieldEnd();
        }
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class MultiGetBuildSlaveRealTimeLogsRequestTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public MultiGetBuildSlaveRealTimeLogsRequestTupleScheme getScheme() {
      return new MultiGetBuildSlaveRealTimeLogsRequestTupleScheme();
    }
  }

  private static class MultiGetBuildSlaveRealTimeLogsRequestTupleScheme extends org.apache.thrift.scheme.TupleScheme<MultiGetBuildSlaveRealTimeLogsRequest> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, MultiGetBuildSlaveRealTimeLogsRequest struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      java.util.BitSet optionals = new java.util.BitSet();
      if (struct.isSetStampedeId()) {
        optionals.set(0);
      }
      if (struct.isSetBatches()) {
        optionals.set(1);
      }
      oprot.writeBitSet(optionals, 2);
      if (struct.isSetStampedeId()) {
        struct.stampedeId.write(oprot);
      }
      if (struct.isSetBatches()) {
        {
          oprot.writeI32(struct.batches.size());
          for (LogLineBatchRequest _iter124 : struct.batches)
          {
            _iter124.write(oprot);
          }
        }
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, MultiGetBuildSlaveRealTimeLogsRequest struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      java.util.BitSet incoming = iprot.readBitSet(2);
      if (incoming.get(0)) {
        struct.stampedeId = new StampedeId();
        struct.stampedeId.read(iprot);
        struct.setStampedeIdIsSet(true);
      }
      if (incoming.get(1)) {
        {
          org.apache.thrift.protocol.TList _list125 = new org.apache.thrift.protocol.TList(org.apache.thrift.protocol.TType.STRUCT, iprot.readI32());
          struct.batches = new java.util.ArrayList<LogLineBatchRequest>(_list125.size);
          LogLineBatchRequest _elem126;
          for (int _i127 = 0; _i127 < _list125.size; ++_i127)
          {
            _elem126 = new LogLineBatchRequest();
            _elem126.read(iprot);
            struct.batches.add(_elem126);
          }
        }
        struct.setBatchesIsSet(true);
      }
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

