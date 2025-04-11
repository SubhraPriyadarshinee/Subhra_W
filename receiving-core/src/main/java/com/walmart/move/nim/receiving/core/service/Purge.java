package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.entity.PurgeData;
import java.util.Calendar;
import java.util.Date;
import org.springframework.data.domain.PageRequest;

/**
 * This is an interface to purge entity
 *
 * @author r0s01us
 */
public interface Purge {

  /**
   * Implement this method to purge an entity. Add mapping of entity to bean in :
   *
   * @see com.walmart.move.nim.receiving.utils.constants.PurgeEntityType
   * @implNote Use following algorithm to implement this: 1.
   *     findByIdGreaterThanEqualTo(purgeEntity.getLastDeletedId,pageRequest) 2. Validate that all
   *     records are created before currentDate - purgeEntitiesBeforeXdays 3. Delete the records and
   *     4. return lastDeletedId
   * @see InstructionPersisterService : Reference implementation
   * @see <a href="https://collaboration.wal-mart.com/display/NGRCV/Purge+Job">Reference docs</a>
   * @returns the lastDeleteId after deletion else return purgeEntity.getLastDeletedId if nothing is
   *     deleted.
   */
  long purge(PurgeData purgeEntity, PageRequest pageRequest, int purgeEntitiesBeforeXdays);

  default Date getPurgeDate(int purgeEntitiesBeforeXdays) {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -purgeEntitiesBeforeXdays * 24);
    return cal.getTime();
  }
}
