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
@Generated(value = "Autogenerated by Thrift Compiler (0.9.3)", date = "2017-12-03")
public class CreateBuildRequest implements org.apache.thrift.TBase<CreateBuildRequest, CreateBuildRequest._Fields>, java.io.Serializable, Cloneable, Comparable<CreateBuildRequest> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("CreateBuildRequest");

  private static final org.apache.thrift.protocol.TField CREATE_TIMESTAMP_MILLIS_FIELD_DESC = new org.apache.thrift.protocol.TField("createTimestampMillis", org.apache.thrift.protocol.TType.I64, (short)1);
  private static final org.apache.thrift.protocol.TField BUILD_MODE_FIELD_DESC = new org.apache.thrift.protocol.TField("buildMode", org.apache.thrift.protocol.TType.I32, (short)2);
  private static final org.apache.thrift.protocol.TField NUMBER_OF_MINIONS_FIELD_DESC = new org.apache.thrift.protocol.TField("numberOfMinions", org.apache.thrift.protocol.TType.I32, (short)3);
  private static final org.apache.thrift.protocol.TField REPOSITORY_FIELD_DESC = new org.apache.thrift.protocol.TField("repository", org.apache.thrift.protocol.TType.STRING, (short)4);
  private static final org.apache.thrift.protocol.TField TENANT_ID_FIELD_DESC = new org.apache.thrift.protocol.TField("tenantId", org.apache.thrift.protocol.TType.STRING, (short)5);
  private static final org.apache.thrift.protocol.TField BUCK_BUILD_UUID_FIELD_DESC = new org.apache.thrift.protocol.TField("buckBuildUuid", org.apache.thrift.protocol.TType.STRING, (short)6);
  private static final org.apache.thrift.protocol.TField USERNAME_FIELD_DESC = new org.apache.thrift.protocol.TField("username", org.apache.thrift.protocol.TType.STRING, (short)7);

  private static final Map<Class<? extends IScheme>, SchemeFactory> schemes = new HashMap<Class<? extends IScheme>, SchemeFactory>();
  static {
    schemes.put(StandardScheme.class, new CreateBuildRequestStandardSchemeFactory());
    schemes.put(TupleScheme.class, new CreateBuildRequestTupleSchemeFactory());
  }

  public long createTimestampMillis; // optional
  /**
   * 
   * @see BuildMode
   */
  public BuildMode buildMode; // optional
  public int numberOfMinions; // optional
  public String repository; // optional
  public String tenantId; // optional
  public String buckBuildUuid; // optional
  public String username; // optional

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    CREATE_TIMESTAMP_MILLIS((short)1, "createTimestampMillis"),
    /**
     * 
     * @see BuildMode
     */
    BUILD_MODE((short)2, "buildMode"),
    NUMBER_OF_MINIONS((short)3, "numberOfMinions"),
    REPOSITORY((short)4, "repository"),
    TENANT_ID((short)5, "tenantId"),
    BUCK_BUILD_UUID((short)6, "buckBuildUuid"),
    USERNAME((short)7, "username");

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
        case 1: // CREATE_TIMESTAMP_MILLIS
          return CREATE_TIMESTAMP_MILLIS;
        case 2: // BUILD_MODE
          return BUILD_MODE;
        case 3: // NUMBER_OF_MINIONS
          return NUMBER_OF_MINIONS;
        case 4: // REPOSITORY
          return REPOSITORY;
        case 5: // TENANT_ID
          return TENANT_ID;
        case 6: // BUCK_BUILD_UUID
          return BUCK_BUILD_UUID;
        case 7: // USERNAME
          return USERNAME;
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
  private static final int __CREATETIMESTAMPMILLIS_ISSET_ID = 0;
  private static final int __NUMBEROFMINIONS_ISSET_ID = 1;
  private byte __isset_bitfield = 0;
  private static final _Fields optionals[] = {_Fields.CREATE_TIMESTAMP_MILLIS,_Fields.BUILD_MODE,_Fields.NUMBER_OF_MINIONS,_Fields.REPOSITORY,_Fields.TENANT_ID,_Fields.BUCK_BUILD_UUID,_Fields.USERNAME};
  public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.CREATE_TIMESTAMP_MILLIS, new org.apache.thrift.meta_data.FieldMetaData("createTimestampMillis", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64)));
    tmpMap.put(_Fields.BUILD_MODE, new org.apache.thrift.meta_data.FieldMetaData("buildMode", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.EnumMetaData(org.apache.thrift.protocol.TType.ENUM, BuildMode.class)));
    tmpMap.put(_Fields.NUMBER_OF_MINIONS, new org.apache.thrift.meta_data.FieldMetaData("numberOfMinions", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I32)));
    tmpMap.put(_Fields.REPOSITORY, new org.apache.thrift.meta_data.FieldMetaData("repository", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.TENANT_ID, new org.apache.thrift.meta_data.FieldMetaData("tenantId", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.BUCK_BUILD_UUID, new org.apache.thrift.meta_data.FieldMetaData("buckBuildUuid", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.USERNAME, new org.apache.thrift.meta_data.FieldMetaData("username", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    metaDataMap = Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(CreateBuildRequest.class, metaDataMap);
  }

  public CreateBuildRequest() {
    this.buildMode = com.facebook.buck.distributed.thrift.BuildMode.REMOTE_BUILD;

  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public CreateBuildRequest(CreateBuildRequest other) {
    __isset_bitfield = other.__isset_bitfield;
    this.createTimestampMillis = other.createTimestampMillis;
    if (other.isSetBuildMode()) {
      this.buildMode = other.buildMode;
    }
    this.numberOfMinions = other.numberOfMinions;
    if (other.isSetRepository()) {
      this.repository = other.repository;
    }
    if (other.isSetTenantId()) {
      this.tenantId = other.tenantId;
    }
    if (other.isSetBuckBuildUuid()) {
      this.buckBuildUuid = other.buckBuildUuid;
    }
    if (other.isSetUsername()) {
      this.username = other.username;
    }
  }

  public CreateBuildRequest deepCopy() {
    return new CreateBuildRequest(this);
  }

  @Override
  public void clear() {
    setCreateTimestampMillisIsSet(false);
    this.createTimestampMillis = 0;
    this.buildMode = com.facebook.buck.distributed.thrift.BuildMode.REMOTE_BUILD;

    setNumberOfMinionsIsSet(false);
    this.numberOfMinions = 0;
    this.repository = null;
    this.tenantId = null;
    this.buckBuildUuid = null;
    this.username = null;
  }

  public long getCreateTimestampMillis() {
    return this.createTimestampMillis;
  }

  public CreateBuildRequest setCreateTimestampMillis(long createTimestampMillis) {
    this.createTimestampMillis = createTimestampMillis;
    setCreateTimestampMillisIsSet(true);
    return this;
  }

  public void unsetCreateTimestampMillis() {
    __isset_bitfield = EncodingUtils.clearBit(__isset_bitfield, __CREATETIMESTAMPMILLIS_ISSET_ID);
  }

  /** Returns true if field createTimestampMillis is set (has been assigned a value) and false otherwise */
  public boolean isSetCreateTimestampMillis() {
    return EncodingUtils.testBit(__isset_bitfield, __CREATETIMESTAMPMILLIS_ISSET_ID);
  }

  public void setCreateTimestampMillisIsSet(boolean value) {
    __isset_bitfield = EncodingUtils.setBit(__isset_bitfield, __CREATETIMESTAMPMILLIS_ISSET_ID, value);
  }

  /**
   * 
   * @see BuildMode
   */
  public BuildMode getBuildMode() {
    return this.buildMode;
  }

  /**
   * 
   * @see BuildMode
   */
  public CreateBuildRequest setBuildMode(BuildMode buildMode) {
    this.buildMode = buildMode;
    return this;
  }

  public void unsetBuildMode() {
    this.buildMode = null;
  }

  /** Returns true if field buildMode is set (has been assigned a value) and false otherwise */
  public boolean isSetBuildMode() {
    return this.buildMode != null;
  }

  public void setBuildModeIsSet(boolean value) {
    if (!value) {
      this.buildMode = null;
    }
  }

  public int getNumberOfMinions() {
    return this.numberOfMinions;
  }

  public CreateBuildRequest setNumberOfMinions(int numberOfMinions) {
    this.numberOfMinions = numberOfMinions;
    setNumberOfMinionsIsSet(true);
    return this;
  }

  public void unsetNumberOfMinions() {
    __isset_bitfield = EncodingUtils.clearBit(__isset_bitfield, __NUMBEROFMINIONS_ISSET_ID);
  }

  /** Returns true if field numberOfMinions is set (has been assigned a value) and false otherwise */
  public boolean isSetNumberOfMinions() {
    return EncodingUtils.testBit(__isset_bitfield, __NUMBEROFMINIONS_ISSET_ID);
  }

  public void setNumberOfMinionsIsSet(boolean value) {
    __isset_bitfield = EncodingUtils.setBit(__isset_bitfield, __NUMBEROFMINIONS_ISSET_ID, value);
  }

  public String getRepository() {
    return this.repository;
  }

  public CreateBuildRequest setRepository(String repository) {
    this.repository = repository;
    return this;
  }

  public void unsetRepository() {
    this.repository = null;
  }

  /** Returns true if field repository is set (has been assigned a value) and false otherwise */
  public boolean isSetRepository() {
    return this.repository != null;
  }

  public void setRepositoryIsSet(boolean value) {
    if (!value) {
      this.repository = null;
    }
  }

  public String getTenantId() {
    return this.tenantId;
  }

  public CreateBuildRequest setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public void unsetTenantId() {
    this.tenantId = null;
  }

  /** Returns true if field tenantId is set (has been assigned a value) and false otherwise */
  public boolean isSetTenantId() {
    return this.tenantId != null;
  }

  public void setTenantIdIsSet(boolean value) {
    if (!value) {
      this.tenantId = null;
    }
  }

  public String getBuckBuildUuid() {
    return this.buckBuildUuid;
  }

  public CreateBuildRequest setBuckBuildUuid(String buckBuildUuid) {
    this.buckBuildUuid = buckBuildUuid;
    return this;
  }

  public void unsetBuckBuildUuid() {
    this.buckBuildUuid = null;
  }

  /** Returns true if field buckBuildUuid is set (has been assigned a value) and false otherwise */
  public boolean isSetBuckBuildUuid() {
    return this.buckBuildUuid != null;
  }

  public void setBuckBuildUuidIsSet(boolean value) {
    if (!value) {
      this.buckBuildUuid = null;
    }
  }

  public String getUsername() {
    return this.username;
  }

  public CreateBuildRequest setUsername(String username) {
    this.username = username;
    return this;
  }

  public void unsetUsername() {
    this.username = null;
  }

  /** Returns true if field username is set (has been assigned a value) and false otherwise */
  public boolean isSetUsername() {
    return this.username != null;
  }

  public void setUsernameIsSet(boolean value) {
    if (!value) {
      this.username = null;
    }
  }

  public void setFieldValue(_Fields field, Object value) {
    switch (field) {
    case CREATE_TIMESTAMP_MILLIS:
      if (value == null) {
        unsetCreateTimestampMillis();
      } else {
        setCreateTimestampMillis((Long)value);
      }
      break;

    case BUILD_MODE:
      if (value == null) {
        unsetBuildMode();
      } else {
        setBuildMode((BuildMode)value);
      }
      break;

    case NUMBER_OF_MINIONS:
      if (value == null) {
        unsetNumberOfMinions();
      } else {
        setNumberOfMinions((Integer)value);
      }
      break;

    case REPOSITORY:
      if (value == null) {
        unsetRepository();
      } else {
        setRepository((String)value);
      }
      break;

    case TENANT_ID:
      if (value == null) {
        unsetTenantId();
      } else {
        setTenantId((String)value);
      }
      break;

    case BUCK_BUILD_UUID:
      if (value == null) {
        unsetBuckBuildUuid();
      } else {
        setBuckBuildUuid((String)value);
      }
      break;

    case USERNAME:
      if (value == null) {
        unsetUsername();
      } else {
        setUsername((String)value);
      }
      break;

    }
  }

  public Object getFieldValue(_Fields field) {
    switch (field) {
    case CREATE_TIMESTAMP_MILLIS:
      return getCreateTimestampMillis();

    case BUILD_MODE:
      return getBuildMode();

    case NUMBER_OF_MINIONS:
      return getNumberOfMinions();

    case REPOSITORY:
      return getRepository();

    case TENANT_ID:
      return getTenantId();

    case BUCK_BUILD_UUID:
      return getBuckBuildUuid();

    case USERNAME:
      return getUsername();

    }
    throw new IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new IllegalArgumentException();
    }

    switch (field) {
    case CREATE_TIMESTAMP_MILLIS:
      return isSetCreateTimestampMillis();
    case BUILD_MODE:
      return isSetBuildMode();
    case NUMBER_OF_MINIONS:
      return isSetNumberOfMinions();
    case REPOSITORY:
      return isSetRepository();
    case TENANT_ID:
      return isSetTenantId();
    case BUCK_BUILD_UUID:
      return isSetBuckBuildUuid();
    case USERNAME:
      return isSetUsername();
    }
    throw new IllegalStateException();
  }

  @Override
  public boolean equals(Object that) {
    if (that == null)
      return false;
    if (that instanceof CreateBuildRequest)
      return this.equals((CreateBuildRequest)that);
    return false;
  }

  public boolean equals(CreateBuildRequest that) {
    if (that == null)
      return false;

    boolean this_present_createTimestampMillis = true && this.isSetCreateTimestampMillis();
    boolean that_present_createTimestampMillis = true && that.isSetCreateTimestampMillis();
    if (this_present_createTimestampMillis || that_present_createTimestampMillis) {
      if (!(this_present_createTimestampMillis && that_present_createTimestampMillis))
        return false;
      if (this.createTimestampMillis != that.createTimestampMillis)
        return false;
    }

    boolean this_present_buildMode = true && this.isSetBuildMode();
    boolean that_present_buildMode = true && that.isSetBuildMode();
    if (this_present_buildMode || that_present_buildMode) {
      if (!(this_present_buildMode && that_present_buildMode))
        return false;
      if (!this.buildMode.equals(that.buildMode))
        return false;
    }

    boolean this_present_numberOfMinions = true && this.isSetNumberOfMinions();
    boolean that_present_numberOfMinions = true && that.isSetNumberOfMinions();
    if (this_present_numberOfMinions || that_present_numberOfMinions) {
      if (!(this_present_numberOfMinions && that_present_numberOfMinions))
        return false;
      if (this.numberOfMinions != that.numberOfMinions)
        return false;
    }

    boolean this_present_repository = true && this.isSetRepository();
    boolean that_present_repository = true && that.isSetRepository();
    if (this_present_repository || that_present_repository) {
      if (!(this_present_repository && that_present_repository))
        return false;
      if (!this.repository.equals(that.repository))
        return false;
    }

    boolean this_present_tenantId = true && this.isSetTenantId();
    boolean that_present_tenantId = true && that.isSetTenantId();
    if (this_present_tenantId || that_present_tenantId) {
      if (!(this_present_tenantId && that_present_tenantId))
        return false;
      if (!this.tenantId.equals(that.tenantId))
        return false;
    }

    boolean this_present_buckBuildUuid = true && this.isSetBuckBuildUuid();
    boolean that_present_buckBuildUuid = true && that.isSetBuckBuildUuid();
    if (this_present_buckBuildUuid || that_present_buckBuildUuid) {
      if (!(this_present_buckBuildUuid && that_present_buckBuildUuid))
        return false;
      if (!this.buckBuildUuid.equals(that.buckBuildUuid))
        return false;
    }

    boolean this_present_username = true && this.isSetUsername();
    boolean that_present_username = true && that.isSetUsername();
    if (this_present_username || that_present_username) {
      if (!(this_present_username && that_present_username))
        return false;
      if (!this.username.equals(that.username))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    List<Object> list = new ArrayList<Object>();

    boolean present_createTimestampMillis = true && (isSetCreateTimestampMillis());
    list.add(present_createTimestampMillis);
    if (present_createTimestampMillis)
      list.add(createTimestampMillis);

    boolean present_buildMode = true && (isSetBuildMode());
    list.add(present_buildMode);
    if (present_buildMode)
      list.add(buildMode.getValue());

    boolean present_numberOfMinions = true && (isSetNumberOfMinions());
    list.add(present_numberOfMinions);
    if (present_numberOfMinions)
      list.add(numberOfMinions);

    boolean present_repository = true && (isSetRepository());
    list.add(present_repository);
    if (present_repository)
      list.add(repository);

    boolean present_tenantId = true && (isSetTenantId());
    list.add(present_tenantId);
    if (present_tenantId)
      list.add(tenantId);

    boolean present_buckBuildUuid = true && (isSetBuckBuildUuid());
    list.add(present_buckBuildUuid);
    if (present_buckBuildUuid)
      list.add(buckBuildUuid);

    boolean present_username = true && (isSetUsername());
    list.add(present_username);
    if (present_username)
      list.add(username);

    return list.hashCode();
  }

  @Override
  public int compareTo(CreateBuildRequest other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = Boolean.valueOf(isSetCreateTimestampMillis()).compareTo(other.isSetCreateTimestampMillis());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetCreateTimestampMillis()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.createTimestampMillis, other.createTimestampMillis);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetBuildMode()).compareTo(other.isSetBuildMode());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetBuildMode()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.buildMode, other.buildMode);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetNumberOfMinions()).compareTo(other.isSetNumberOfMinions());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetNumberOfMinions()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.numberOfMinions, other.numberOfMinions);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetRepository()).compareTo(other.isSetRepository());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetRepository()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.repository, other.repository);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetTenantId()).compareTo(other.isSetTenantId());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetTenantId()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.tenantId, other.tenantId);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetBuckBuildUuid()).compareTo(other.isSetBuckBuildUuid());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetBuckBuildUuid()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.buckBuildUuid, other.buckBuildUuid);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetUsername()).compareTo(other.isSetUsername());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetUsername()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.username, other.username);
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
    StringBuilder sb = new StringBuilder("CreateBuildRequest(");
    boolean first = true;

    if (isSetCreateTimestampMillis()) {
      sb.append("createTimestampMillis:");
      sb.append(this.createTimestampMillis);
      first = false;
    }
    if (isSetBuildMode()) {
      if (!first) sb.append(", ");
      sb.append("buildMode:");
      if (this.buildMode == null) {
        sb.append("null");
      } else {
        sb.append(this.buildMode);
      }
      first = false;
    }
    if (isSetNumberOfMinions()) {
      if (!first) sb.append(", ");
      sb.append("numberOfMinions:");
      sb.append(this.numberOfMinions);
      first = false;
    }
    if (isSetRepository()) {
      if (!first) sb.append(", ");
      sb.append("repository:");
      if (this.repository == null) {
        sb.append("null");
      } else {
        sb.append(this.repository);
      }
      first = false;
    }
    if (isSetTenantId()) {
      if (!first) sb.append(", ");
      sb.append("tenantId:");
      if (this.tenantId == null) {
        sb.append("null");
      } else {
        sb.append(this.tenantId);
      }
      first = false;
    }
    if (isSetBuckBuildUuid()) {
      if (!first) sb.append(", ");
      sb.append("buckBuildUuid:");
      if (this.buckBuildUuid == null) {
        sb.append("null");
      } else {
        sb.append(this.buckBuildUuid);
      }
      first = false;
    }
    if (isSetUsername()) {
      if (!first) sb.append(", ");
      sb.append("username:");
      if (this.username == null) {
        sb.append("null");
      } else {
        sb.append(this.username);
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
      // it doesn't seem like you should have to do this, but java serialization is wacky, and doesn't call the default constructor.
      __isset_bitfield = 0;
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class CreateBuildRequestStandardSchemeFactory implements SchemeFactory {
    public CreateBuildRequestStandardScheme getScheme() {
      return new CreateBuildRequestStandardScheme();
    }
  }

  private static class CreateBuildRequestStandardScheme extends StandardScheme<CreateBuildRequest> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, CreateBuildRequest struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // CREATE_TIMESTAMP_MILLIS
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.createTimestampMillis = iprot.readI64();
              struct.setCreateTimestampMillisIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // BUILD_MODE
            if (schemeField.type == org.apache.thrift.protocol.TType.I32) {
              struct.buildMode = com.facebook.buck.distributed.thrift.BuildMode.findByValue(iprot.readI32());
              struct.setBuildModeIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 3: // NUMBER_OF_MINIONS
            if (schemeField.type == org.apache.thrift.protocol.TType.I32) {
              struct.numberOfMinions = iprot.readI32();
              struct.setNumberOfMinionsIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 4: // REPOSITORY
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.repository = iprot.readString();
              struct.setRepositoryIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 5: // TENANT_ID
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.tenantId = iprot.readString();
              struct.setTenantIdIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 6: // BUCK_BUILD_UUID
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.buckBuildUuid = iprot.readString();
              struct.setBuckBuildUuidIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 7: // USERNAME
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.username = iprot.readString();
              struct.setUsernameIsSet(true);
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

    public void write(org.apache.thrift.protocol.TProtocol oprot, CreateBuildRequest struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.isSetCreateTimestampMillis()) {
        oprot.writeFieldBegin(CREATE_TIMESTAMP_MILLIS_FIELD_DESC);
        oprot.writeI64(struct.createTimestampMillis);
        oprot.writeFieldEnd();
      }
      if (struct.buildMode != null) {
        if (struct.isSetBuildMode()) {
          oprot.writeFieldBegin(BUILD_MODE_FIELD_DESC);
          oprot.writeI32(struct.buildMode.getValue());
          oprot.writeFieldEnd();
        }
      }
      if (struct.isSetNumberOfMinions()) {
        oprot.writeFieldBegin(NUMBER_OF_MINIONS_FIELD_DESC);
        oprot.writeI32(struct.numberOfMinions);
        oprot.writeFieldEnd();
      }
      if (struct.repository != null) {
        if (struct.isSetRepository()) {
          oprot.writeFieldBegin(REPOSITORY_FIELD_DESC);
          oprot.writeString(struct.repository);
          oprot.writeFieldEnd();
        }
      }
      if (struct.tenantId != null) {
        if (struct.isSetTenantId()) {
          oprot.writeFieldBegin(TENANT_ID_FIELD_DESC);
          oprot.writeString(struct.tenantId);
          oprot.writeFieldEnd();
        }
      }
      if (struct.buckBuildUuid != null) {
        if (struct.isSetBuckBuildUuid()) {
          oprot.writeFieldBegin(BUCK_BUILD_UUID_FIELD_DESC);
          oprot.writeString(struct.buckBuildUuid);
          oprot.writeFieldEnd();
        }
      }
      if (struct.username != null) {
        if (struct.isSetUsername()) {
          oprot.writeFieldBegin(USERNAME_FIELD_DESC);
          oprot.writeString(struct.username);
          oprot.writeFieldEnd();
        }
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class CreateBuildRequestTupleSchemeFactory implements SchemeFactory {
    public CreateBuildRequestTupleScheme getScheme() {
      return new CreateBuildRequestTupleScheme();
    }
  }

  private static class CreateBuildRequestTupleScheme extends TupleScheme<CreateBuildRequest> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, CreateBuildRequest struct) throws org.apache.thrift.TException {
      TTupleProtocol oprot = (TTupleProtocol) prot;
      BitSet optionals = new BitSet();
      if (struct.isSetCreateTimestampMillis()) {
        optionals.set(0);
      }
      if (struct.isSetBuildMode()) {
        optionals.set(1);
      }
      if (struct.isSetNumberOfMinions()) {
        optionals.set(2);
      }
      if (struct.isSetRepository()) {
        optionals.set(3);
      }
      if (struct.isSetTenantId()) {
        optionals.set(4);
      }
      if (struct.isSetBuckBuildUuid()) {
        optionals.set(5);
      }
      if (struct.isSetUsername()) {
        optionals.set(6);
      }
      oprot.writeBitSet(optionals, 7);
      if (struct.isSetCreateTimestampMillis()) {
        oprot.writeI64(struct.createTimestampMillis);
      }
      if (struct.isSetBuildMode()) {
        oprot.writeI32(struct.buildMode.getValue());
      }
      if (struct.isSetNumberOfMinions()) {
        oprot.writeI32(struct.numberOfMinions);
      }
      if (struct.isSetRepository()) {
        oprot.writeString(struct.repository);
      }
      if (struct.isSetTenantId()) {
        oprot.writeString(struct.tenantId);
      }
      if (struct.isSetBuckBuildUuid()) {
        oprot.writeString(struct.buckBuildUuid);
      }
      if (struct.isSetUsername()) {
        oprot.writeString(struct.username);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, CreateBuildRequest struct) throws org.apache.thrift.TException {
      TTupleProtocol iprot = (TTupleProtocol) prot;
      BitSet incoming = iprot.readBitSet(7);
      if (incoming.get(0)) {
        struct.createTimestampMillis = iprot.readI64();
        struct.setCreateTimestampMillisIsSet(true);
      }
      if (incoming.get(1)) {
        struct.buildMode = com.facebook.buck.distributed.thrift.BuildMode.findByValue(iprot.readI32());
        struct.setBuildModeIsSet(true);
      }
      if (incoming.get(2)) {
        struct.numberOfMinions = iprot.readI32();
        struct.setNumberOfMinionsIsSet(true);
      }
      if (incoming.get(3)) {
        struct.repository = iprot.readString();
        struct.setRepositoryIsSet(true);
      }
      if (incoming.get(4)) {
        struct.tenantId = iprot.readString();
        struct.setTenantIdIsSet(true);
      }
      if (incoming.get(5)) {
        struct.buckBuildUuid = iprot.readString();
        struct.setBuckBuildUuidIsSet(true);
      }
      if (incoming.get(6)) {
        struct.username = iprot.readString();
        struct.setUsernameIsSet(true);
      }
    }
  }

}

