/**
 * Autogenerated by Thrift Compiler (0.10.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.facebook.buck.rules.modern.builders.thrift;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
@javax.annotation.Generated(value = "Autogenerated by Thrift Compiler (0.10.0)")
public class FileNode implements org.apache.thrift.TBase<FileNode, FileNode._Fields>, java.io.Serializable, Cloneable, Comparable<FileNode> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("FileNode");

  private static final org.apache.thrift.protocol.TField NAME_FIELD_DESC = new org.apache.thrift.protocol.TField("name", org.apache.thrift.protocol.TType.STRING, (short)1);
  private static final org.apache.thrift.protocol.TField DIGEST_FIELD_DESC = new org.apache.thrift.protocol.TField("digest", org.apache.thrift.protocol.TType.STRUCT, (short)2);
  private static final org.apache.thrift.protocol.TField IS_EXECUTABLE_FIELD_DESC = new org.apache.thrift.protocol.TField("isExecutable", org.apache.thrift.protocol.TType.BOOL, (short)3);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new FileNodeStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new FileNodeTupleSchemeFactory();

  public java.lang.String name; // required
  public Digest digest; // required
  public boolean isExecutable; // required

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    NAME((short)1, "name"),
    DIGEST((short)2, "digest"),
    IS_EXECUTABLE((short)3, "isExecutable");

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
        case 1: // NAME
          return NAME;
        case 2: // DIGEST
          return DIGEST;
        case 3: // IS_EXECUTABLE
          return IS_EXECUTABLE;
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
  private static final int __ISEXECUTABLE_ISSET_ID = 0;
  private byte __isset_bitfield = 0;
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.NAME, new org.apache.thrift.meta_data.FieldMetaData("name", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.DIGEST, new org.apache.thrift.meta_data.FieldMetaData("digest", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, Digest.class)));
    tmpMap.put(_Fields.IS_EXECUTABLE, new org.apache.thrift.meta_data.FieldMetaData("isExecutable", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.BOOL)));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(FileNode.class, metaDataMap);
  }

  public FileNode() {
  }

  public FileNode(
    java.lang.String name,
    Digest digest,
    boolean isExecutable)
  {
    this();
    this.name = name;
    this.digest = digest;
    this.isExecutable = isExecutable;
    setIsExecutableIsSet(true);
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public FileNode(FileNode other) {
    __isset_bitfield = other.__isset_bitfield;
    if (other.isSetName()) {
      this.name = other.name;
    }
    if (other.isSetDigest()) {
      this.digest = new Digest(other.digest);
    }
    this.isExecutable = other.isExecutable;
  }

  public FileNode deepCopy() {
    return new FileNode(this);
  }

  @Override
  public void clear() {
    this.name = null;
    this.digest = null;
    setIsExecutableIsSet(false);
    this.isExecutable = false;
  }

  public java.lang.String getName() {
    return this.name;
  }

  public FileNode setName(java.lang.String name) {
    this.name = name;
    return this;
  }

  public void unsetName() {
    this.name = null;
  }

  /** Returns true if field name is set (has been assigned a value) and false otherwise */
  public boolean isSetName() {
    return this.name != null;
  }

  public void setNameIsSet(boolean value) {
    if (!value) {
      this.name = null;
    }
  }

  public Digest getDigest() {
    return this.digest;
  }

  public FileNode setDigest(Digest digest) {
    this.digest = digest;
    return this;
  }

  public void unsetDigest() {
    this.digest = null;
  }

  /** Returns true if field digest is set (has been assigned a value) and false otherwise */
  public boolean isSetDigest() {
    return this.digest != null;
  }

  public void setDigestIsSet(boolean value) {
    if (!value) {
      this.digest = null;
    }
  }

  public boolean isIsExecutable() {
    return this.isExecutable;
  }

  public FileNode setIsExecutable(boolean isExecutable) {
    this.isExecutable = isExecutable;
    setIsExecutableIsSet(true);
    return this;
  }

  public void unsetIsExecutable() {
    __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __ISEXECUTABLE_ISSET_ID);
  }

  /** Returns true if field isExecutable is set (has been assigned a value) and false otherwise */
  public boolean isSetIsExecutable() {
    return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __ISEXECUTABLE_ISSET_ID);
  }

  public void setIsExecutableIsSet(boolean value) {
    __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __ISEXECUTABLE_ISSET_ID, value);
  }

  public void setFieldValue(_Fields field, java.lang.Object value) {
    switch (field) {
    case NAME:
      if (value == null) {
        unsetName();
      } else {
        setName((java.lang.String)value);
      }
      break;

    case DIGEST:
      if (value == null) {
        unsetDigest();
      } else {
        setDigest((Digest)value);
      }
      break;

    case IS_EXECUTABLE:
      if (value == null) {
        unsetIsExecutable();
      } else {
        setIsExecutable((java.lang.Boolean)value);
      }
      break;

    }
  }

  public java.lang.Object getFieldValue(_Fields field) {
    switch (field) {
    case NAME:
      return getName();

    case DIGEST:
      return getDigest();

    case IS_EXECUTABLE:
      return isIsExecutable();

    }
    throw new java.lang.IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new java.lang.IllegalArgumentException();
    }

    switch (field) {
    case NAME:
      return isSetName();
    case DIGEST:
      return isSetDigest();
    case IS_EXECUTABLE:
      return isSetIsExecutable();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that == null)
      return false;
    if (that instanceof FileNode)
      return this.equals((FileNode)that);
    return false;
  }

  public boolean equals(FileNode that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_name = true && this.isSetName();
    boolean that_present_name = true && that.isSetName();
    if (this_present_name || that_present_name) {
      if (!(this_present_name && that_present_name))
        return false;
      if (!this.name.equals(that.name))
        return false;
    }

    boolean this_present_digest = true && this.isSetDigest();
    boolean that_present_digest = true && that.isSetDigest();
    if (this_present_digest || that_present_digest) {
      if (!(this_present_digest && that_present_digest))
        return false;
      if (!this.digest.equals(that.digest))
        return false;
    }

    boolean this_present_isExecutable = true;
    boolean that_present_isExecutable = true;
    if (this_present_isExecutable || that_present_isExecutable) {
      if (!(this_present_isExecutable && that_present_isExecutable))
        return false;
      if (this.isExecutable != that.isExecutable)
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + ((isSetName()) ? 131071 : 524287);
    if (isSetName())
      hashCode = hashCode * 8191 + name.hashCode();

    hashCode = hashCode * 8191 + ((isSetDigest()) ? 131071 : 524287);
    if (isSetDigest())
      hashCode = hashCode * 8191 + digest.hashCode();

    hashCode = hashCode * 8191 + ((isExecutable) ? 131071 : 524287);

    return hashCode;
  }

  @Override
  public int compareTo(FileNode other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.valueOf(isSetName()).compareTo(other.isSetName());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetName()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.name, other.name);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.valueOf(isSetDigest()).compareTo(other.isSetDigest());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetDigest()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.digest, other.digest);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.valueOf(isSetIsExecutable()).compareTo(other.isSetIsExecutable());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetIsExecutable()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.isExecutable, other.isExecutable);
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
    java.lang.StringBuilder sb = new java.lang.StringBuilder("FileNode(");
    boolean first = true;

    sb.append("name:");
    if (this.name == null) {
      sb.append("null");
    } else {
      sb.append(this.name);
    }
    first = false;
    if (!first) sb.append(", ");
    sb.append("digest:");
    if (this.digest == null) {
      sb.append("null");
    } else {
      sb.append(this.digest);
    }
    first = false;
    if (!first) sb.append(", ");
    sb.append("isExecutable:");
    sb.append(this.isExecutable);
    first = false;
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // check for sub-struct validity
    if (digest != null) {
      digest.validate();
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

  private static class FileNodeStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public FileNodeStandardScheme getScheme() {
      return new FileNodeStandardScheme();
    }
  }

  private static class FileNodeStandardScheme extends org.apache.thrift.scheme.StandardScheme<FileNode> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, FileNode struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // NAME
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.name = iprot.readString();
              struct.setNameIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // DIGEST
            if (schemeField.type == org.apache.thrift.protocol.TType.STRUCT) {
              struct.digest = new Digest();
              struct.digest.read(iprot);
              struct.setDigestIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 3: // IS_EXECUTABLE
            if (schemeField.type == org.apache.thrift.protocol.TType.BOOL) {
              struct.isExecutable = iprot.readBool();
              struct.setIsExecutableIsSet(true);
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

    public void write(org.apache.thrift.protocol.TProtocol oprot, FileNode struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.name != null) {
        oprot.writeFieldBegin(NAME_FIELD_DESC);
        oprot.writeString(struct.name);
        oprot.writeFieldEnd();
      }
      if (struct.digest != null) {
        oprot.writeFieldBegin(DIGEST_FIELD_DESC);
        struct.digest.write(oprot);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldBegin(IS_EXECUTABLE_FIELD_DESC);
      oprot.writeBool(struct.isExecutable);
      oprot.writeFieldEnd();
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class FileNodeTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public FileNodeTupleScheme getScheme() {
      return new FileNodeTupleScheme();
    }
  }

  private static class FileNodeTupleScheme extends org.apache.thrift.scheme.TupleScheme<FileNode> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, FileNode struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      java.util.BitSet optionals = new java.util.BitSet();
      if (struct.isSetName()) {
        optionals.set(0);
      }
      if (struct.isSetDigest()) {
        optionals.set(1);
      }
      if (struct.isSetIsExecutable()) {
        optionals.set(2);
      }
      oprot.writeBitSet(optionals, 3);
      if (struct.isSetName()) {
        oprot.writeString(struct.name);
      }
      if (struct.isSetDigest()) {
        struct.digest.write(oprot);
      }
      if (struct.isSetIsExecutable()) {
        oprot.writeBool(struct.isExecutable);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, FileNode struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      java.util.BitSet incoming = iprot.readBitSet(3);
      if (incoming.get(0)) {
        struct.name = iprot.readString();
        struct.setNameIsSet(true);
      }
      if (incoming.get(1)) {
        struct.digest = new Digest();
        struct.digest.read(iprot);
        struct.setDigestIsSet(true);
      }
      if (incoming.get(2)) {
        struct.isExecutable = iprot.readBool();
        struct.setIsExecutableIsSet(true);
      }
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

