import com.foodloop.util.GeoUtils;
import com.foodloop.entity.enums.DonationStatus;
import static com.foodloop.entity.enums.DonationStatus.*;
public class Check {
  static int fails=0;
  static void ok(boolean c,String m){ System.out.println((c?"PASS ":"FAIL ")+m); if(!c) fails++; }
  public static void main(String[] a){
    ok(Math.abs(GeoUtils.haversineKm(11.0074,76.95,11.0074,76.95))<1e-9,"same point = 0");
    double d=GeoUtils.haversineKm(11.0074,76.95,10.999,77.03); System.out.println("RS Puram -> Singanallur = "+d+" km");
    ok(Math.abs(d-8.7)<0.5,"RS Puram-Singanallur ~8.7 km");
    ok(Math.abs(GeoUtils.haversineKm(11.0,76.9,11.1,77.1)-GeoUtils.haversineKm(11.1,77.1,11.0,76.9))<1e-9,"symmetric");
    ok(AVAILABLE.canTransitionTo(MATCHED)&&MATCHED.canTransitionTo(ACCEPTED)&&ACCEPTED.canTransitionTo(VOLUNTEER_ASSIGNED)&&VOLUNTEER_ASSIGNED.canTransitionTo(PICKED_UP)&&PICKED_UP.canTransitionTo(IN_TRANSIT)&&IN_TRANSIT.canTransitionTo(DELIVERED)&&DELIVERED.canTransitionTo(COMPLETED),"happy path");
    ok(!AVAILABLE.canTransitionTo(COMPLETED)&&!ACCEPTED.canTransitionTo(DELIVERED),"no skipping");
    ok(MATCHED.isCancellable()&&VOLUNTEER_ASSIGNED.isCancellable()&&!PICKED_UP.isCancellable()&&!IN_TRANSIT.isCancellable(),"cancel only before pickup");
    ok(COMPLETED.isTerminal()&&CANCELLED.isTerminal()&&EXPIRED.isTerminal()&&!MATCHED.isTerminal(),"terminal states");
    System.out.println(fails==0?"ALL PASSED":"FAILURES: "+fails); System.exit(fails);
  }
}
