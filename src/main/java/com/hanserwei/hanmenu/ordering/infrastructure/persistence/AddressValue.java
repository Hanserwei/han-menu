package com.hanserwei.hanmenu.ordering.infrastructure.persistence;

import com.hanserwei.hanmenu.ordering.domain.AddressSnapshot;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/** ORM 内嵌收货快照，与领域值对象分离. */
@Embeddable
public class AddressValue {
  UUID sourceId;
  long sourceVersion;
  String recipientName;
  String phone;
  String province;
  String city;
  String district;
  String detail;

  /** ORM 重建入口. */
  protected AddressValue() {}

  static AddressValue from(AddressSnapshot value) {
    var entity = new AddressValue();
    entity.sourceId = value.sourceId();
    entity.sourceVersion = value.sourceVersion();
    entity.recipientName = value.recipientName();
    entity.phone = value.phone();
    entity.province = value.province();
    entity.city = value.city();
    entity.district = value.district();
    entity.detail = value.detail();
    return entity;
  }

  AddressSnapshot domain() {
    return new AddressSnapshot(
        sourceId, sourceVersion, recipientName, phone, province, city, district, detail);
  }
}
