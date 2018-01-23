/**
 * Autogenerated by Thrift Compiler (0.9.3)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.facebook.buck.distributed.thrift;

import org.apache.thrift.scheme.IScheme;
import org.apache.thrift.scheme.SchemeFactory;
import org.apache.thrift.scheme.StandardScheme;

import org.apache.thrift.scheme.TupleScheme;
import org.apache.thrift.protocol.TTupleProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.apache.thrift.EncodingUtils;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.server.AbstractNonblockingServer.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Set;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.Collections;
import java.util.BitSet;
import java.nio.ByteBuffer;
import java.util.Arrays;
import javax.annotation.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked"})
@Generated(value = "Autogenerated by Thrift Compiler (0.9.3)", date = "2018-01-19")
public class MultiGetBuildSlaveRealTimeLogsResponse implements org.apache.thrift.TBase<MultiGetBuildSlaveRealTimeLogsResponse, MultiGetBuildSlaveRealTimeLogsResponse._Fields>, java.io.Serializable, Cloneable, Comparable<MultiGetBuildSlaveRealTimeLogsResponse> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("MultiGetBuildSlaveRealTimeLogsResponse");

  private static final org.apache.thrift.protocol.TField MULTI_STREAM_LOGS_FIELD_DESC = new org.apache.thrift.protocol.TField("multiStreamLogs", org.apache.thrift.protocol.TType.LIST, (short)1);

  private static final Map<Class<? extends IScheme>, SchemeFactory> schemes = new HashMap<Class<? extends IScheme>, SchemeFactory>();
  static {
    schemes.put(StandardScheme.class, new MultiGetBuildSlaveRealTimeLogsResponseStandardSchemeFactory());
    schemes.put(TupleScheme.class, new MultiGetBuildSlaveRealTimeLogsResponseTupleSchemeFactory());
  }

  public List<StreamLogs> multiStreamLogs; // optional

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    MULTI_STREAM_LOGS((short)1, "multiStreamLogs");

    private static final Map<String, _Fields> byName = new HashMap<String, _Fields>();

    static {
      for (_Fields field : EnumSet.allOf(_Fields.class)) {
        byName.put(field.getFieldName(), field);
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, or null if its not found.
     */
    public static _Fields findByThriftId(int fieldId) {
      switch(fieldId) {
        case 1: // MULTI_STREAM_LOGS
          return MULTI_STREAM_LOGS;
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
      if (fields == null) throw new IllegalArgumentException("Field " + fieldId + " doesn't exist!");
      return fields;
    }

    /**
     * Find the _Fields constant that matches name, or null if its not found.
     */
    public static _Fields findByName(String name) {
      return byName.get(name);
    }

    private final short _thriftId;
    private final String _fieldName;

    _Fields(short thriftId, String fieldName) {
      _thriftId = thriftId;
      _fieldName = fieldName;
    }

    public short getThriftFieldId() {
      return _thriftId;
    }

    public String getFieldName() {
      return _fieldName;
    }
  }

  // isset id assignments
  private static final _Fields optionals[] = {_Fields.MULTI_STREAM_LOGS};
  public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.MULTI_STREAM_LOGS, new org.apache.thrift.meta_data.FieldMetaData("multiStreamLogs", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.ListMetaData(org.apache.thrift.protocol.TType.LIST, 
            new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, StreamLogs.class))));
    metaDataMap = Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(MultiGetBuildSlaveRealTimeLogsResponse.class, metaDataMap);
  }

  public MultiGetBuildSlaveRealTimeLogsResponse() {
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public MultiGetBuildSlaveRealTimeLogsResponse(MultiGetBuildSlaveRealTimeLogsResponse other) {
    if (other.isSetMultiStreamLogs()) {
      List<StreamLogs> __this__multiStreamLogs = new ArrayList<StreamLogs>(other.multiStreamLogs.size());
      for (StreamLogs other_element : other.multiStreamLogs) {
        __this__multiStreamLogs.add(new StreamLogs(other_element));
      }
      this.multiStreamLogs = __this__multiStreamLogs;
    }
  }

  public MultiGetBuildSlaveRealTimeLogsResponse deepCopy() {
    return new MultiGetBuildSlaveRealTimeLogsResponse(this);
  }

  @Override
  public void clear() {
    this.multiStreamLogs = null;
  }

  public int getMultiStreamLogsSize() {
    return (this.multiStreamLogs == null) ? 0 : this.multiStreamLogs.size();
  }

  public java.util.Iterator<StreamLogs> getMultiStreamLogsIterator() {
    return (this.multiStreamLogs == null) ? null : this.multiStreamLogs.iterator();
  }

  public void addToMultiStreamLogs(StreamLogs elem) {
    if (this.multiStreamLogs == null) {
      this.multiStreamLogs = new ArrayList<StreamLogs>();
    }
    this.multiStreamLogs.add(elem);
  }

  public List<StreamLogs> getMultiStreamLogs() {
    return this.multiStreamLogs;
  }

  public MultiGetBuildSlaveRealTimeLogsResponse setMultiStreamLogs(List<StreamLogs> multiStreamLogs) {
    this.multiStreamLogs = multiStreamLogs;
    return this;
  }

  public void unsetMultiStreamLogs() {
    this.multiStreamLogs = null;
  }

  /** Returns true if field multiStreamLogs is set (has been assigned a value) and false otherwise */
  public boolean isSetMultiStreamLogs() {
    return this.multiStreamLogs != null;
  }

  public void setMultiStreamLogsIsSet(boolean value) {
    if (!value) {
      this.multiStreamLogs = null;
    }
  }

  public void setFieldValue(_Fields field, Object value) {
    switch (field) {
    case MULTI_STREAM_LOGS:
      if (value == null) {
        unsetMultiStreamLogs();
      } else {
        setMultiStreamLogs((List<StreamLogs>)value);
      }
      break;

    }
  }

  public Object getFieldValue(_Fields field) {
    switch (field) {
    case MULTI_STREAM_LOGS:
      return getMultiStreamLogs();

    }
    throw new IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new IllegalArgumentException();
    }

    switch (field) {
    case MULTI_STREAM_LOGS:
      return isSetMultiStreamLogs();
    }
    throw new IllegalStateException();
  }

  @Override
  public boolean equals(Object that) {
    if (that == null)
      return false;
    if (that instanceof MultiGetBuildSlaveRealTimeLogsResponse)
      return this.equals((MultiGetBuildSlaveRealTimeLogsResponse)that);
    return false;
  }

  public boolean equals(MultiGetBuildSlaveRealTimeLogsResponse that) {
    if (that == null)
      return false;

    boolean this_present_multiStreamLogs = true && this.isSetMultiStreamLogs();
    boolean that_present_multiStreamLogs = true && that.isSetMultiStreamLogs();
    if (this_present_multiStreamLogs || that_present_multiStreamLogs) {
      if (!(this_present_multiStreamLogs && that_present_multiStreamLogs))
        return false;
      if (!this.multiStreamLogs.equals(that.multiStreamLogs))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    List<Object> list = new ArrayList<Object>();

    boolean present_multiStreamLogs = true && (isSetMultiStreamLogs());
    list.add(present_multiStreamLogs);
    if (present_multiStreamLogs)
      list.add(multiStreamLogs);

    return list.hashCode();
  }

  @Override
  public int compareTo(MultiGetBuildSlaveRealTimeLogsResponse other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = Boolean.valueOf(isSetMultiStreamLogs()).compareTo(other.isSetMultiStreamLogs());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetMultiStreamLogs()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.multiStreamLogs, other.multiStreamLogs);
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
    schemes.get(iprot.getScheme()).getScheme().read(iprot, this);
  }

  public void write(org.apache.thrift.protocol.TProtocol oprot) throws org.apache.thrift.TException {
    schemes.get(oprot.getScheme()).getScheme().write(oprot, this);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("MultiGetBuildSlaveRealTimeLogsResponse(");
    boolean first = true;

    if (isSetMultiStreamLogs()) {
      sb.append("multiStreamLogs:");
      if (this.multiStreamLogs == null) {
        sb.append("null");
      } else {
        sb.append(this.multiStreamLogs);
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

  private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
    try {
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class MultiGetBuildSlaveRealTimeLogsResponseStandardSchemeFactory implements SchemeFactory {
    public MultiGetBuildSlaveRealTimeLogsResponseStandardScheme getScheme() {
      return new MultiGetBuildSlaveRealTimeLogsResponseStandardScheme();
    }
  }

  private static class MultiGetBuildSlaveRealTimeLogsResponseStandardScheme extends StandardScheme<MultiGetBuildSlaveRealTimeLogsResponse> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, MultiGetBuildSlaveRealTimeLogsResponse struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // MULTI_STREAM_LOGS
            if (schemeField.type == org.apache.thrift.protocol.TType.LIST) {
              {
                org.apache.thrift.protocol.TList _list130 = iprot.readListBegin();
                struct.multiStreamLogs = new ArrayList<StreamLogs>(_list130.size);
                StreamLogs _elem131;
                for (int _i132 = 0; _i132 < _list130.size; ++_i132)
                {
                  _elem131 = new StreamLogs();
                  _elem131.read(iprot);
                  struct.multiStreamLogs.add(_elem131);
                }
                iprot.readListEnd();
              }
              struct.setMultiStreamLogsIsSet(true);
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

    public void write(org.apache.thrift.protocol.TProtocol oprot, MultiGetBuildSlaveRealTimeLogsResponse struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.multiStreamLogs != null) {
        if (struct.isSetMultiStreamLogs()) {
          oprot.writeFieldBegin(MULTI_STREAM_LOGS_FIELD_DESC);
          {
            oprot.writeListBegin(new org.apache.thrift.protocol.TList(org.apache.thrift.protocol.TType.STRUCT, struct.multiStreamLogs.size()));
            for (StreamLogs _iter133 : struct.multiStreamLogs)
            {
              _iter133.write(oprot);
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

  private static class MultiGetBuildSlaveRealTimeLogsResponseTupleSchemeFactory implements SchemeFactory {
    public MultiGetBuildSlaveRealTimeLogsResponseTupleScheme getScheme() {
      return new MultiGetBuildSlaveRealTimeLogsResponseTupleScheme();
    }
  }

  private static class MultiGetBuildSlaveRealTimeLogsResponseTupleScheme extends TupleScheme<MultiGetBuildSlaveRealTimeLogsResponse> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, MultiGetBuildSlaveRealTimeLogsResponse struct) throws org.apache.thrift.TException {
      TTupleProtocol oprot = (TTupleProtocol) prot;
      BitSet optionals = new BitSet();
      if (struct.isSetMultiStreamLogs()) {
        optionals.set(0);
      }
      oprot.writeBitSet(optionals, 1);
      if (struct.isSetMultiStreamLogs()) {
        {
          oprot.writeI32(struct.multiStreamLogs.size());
          for (StreamLogs _iter134 : struct.multiStreamLogs)
          {
            _iter134.write(oprot);
          }
        }
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, MultiGetBuildSlaveRealTimeLogsResponse struct) throws org.apache.thrift.TException {
      TTupleProtocol iprot = (TTupleProtocol) prot;
      BitSet incoming = iprot.readBitSet(1);
      if (incoming.get(0)) {
        {
          org.apache.thrift.protocol.TList _list135 = new org.apache.thrift.protocol.TList(org.apache.thrift.protocol.TType.STRUCT, iprot.readI32());
          struct.multiStreamLogs = new ArrayList<StreamLogs>(_list135.size);
          StreamLogs _elem136;
          for (int _i137 = 0; _i137 < _list135.size; ++_i137)
          {
            _elem136 = new StreamLogs();
            _elem136.read(iprot);
            struct.multiStreamLogs.add(_elem136);
          }
        }
        struct.setMultiStreamLogsIsSet(true);
      }
    }
  }

}

