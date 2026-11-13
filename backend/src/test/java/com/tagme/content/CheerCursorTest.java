package com.tagme.content;
import org.junit.jupiter.api.Test;
import static com.tagme.content.CheerFeedService.*;
import static org.assertj.core.api.Assertions.*;
class CheerCursorTest {
 @Test void roundTripsEachSortAndDirectionWithoutSharingCursors() {
  for(var sort:Sort.values())for(var direction:Direction.values()) {
   var c=new Cursor(100,50,3);assertThat(Cursor.parse(c.encode(sort,direction),sort,direction)).isEqualTo(c);
   assertThatThrownBy(()->Cursor.parse(c.encode(sort,direction),sort,direction==Direction.ASC?Direction.DESC:Direction.ASC)).isInstanceOf(com.tagme.auth.AuthException.class);
  }
 }
 @Test void malformedBoundsOverflowAndUntrustedSortFragmentsAreRejected() {
  for(String value:new String[]{"","0","-1","v1.LATEST.DESC.1.2.0","v1.LATEST.DESC.2.1.-1","v1.LATEST.DESC.99999999999999999999.1.0","v1.LATEST.DESC.2.1.0.extra","v1.LATEST.DESC.2.1.0 OR 1=1"})
   assertThatThrownBy(()->Cursor.parse(value,Sort.LATEST,Direction.DESC)).isInstanceOf(com.tagme.auth.AuthException.class);
  assertThat(Cursor.parse("12",Sort.LATEST,Direction.DESC).id()).isEqualTo(12);
 }
}
