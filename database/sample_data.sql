-- =====================================================================
-- FoodLoop AI - SAMPLE DEVELOPMENT DATA   (run AFTER schema.sql)
-- ALL organisations, people, emails, phone numbers and quantities below are
-- FICTIONAL and exist only for development / demo. Coordinates are
-- approximate Coimbatore area centres, not exact addresses.
-- Demo password for EVERY account:  Password@123   (stored as BCrypt hash)
-- =====================================================================
USE foodloop_ai;

INSERT INTO roles (id, name, description) VALUES
 (1,'ADMIN','Platform administrator'),
 (2,'DONOR','Restaurant, hotel, bakery, supermarket, event organizer'),
 (3,'NGO','NGO, charity or food bank receiving food'),
 (4,'VOLUNTEER','Person who collects and delivers food');

-- ---------- Users (1 admin, 4 donors, 4 NGOs, 3 volunteers) ----------
SET @pw = '$2a$10$t4tvmJeBrtE5ZDX1S3oK5ux/Qr3LJ1r3fLUM5Bk.h/Z4G1KzlYp2m';
INSERT INTO users (id,email,password_hash,full_name,phone,role_id,status,email_verified,created_at) VALUES
 (1 ,'admin@foodloop.test'      ,@pw,'FoodLoop Admin'      ,'9000000001',1,'ACTIVE' ,1,NOW()-INTERVAL 180 DAY),
 (2 ,'annapoorna@foodloop.test' ,@pw,'Ravi Kumar'          ,'9000000002',2,'ACTIVE' ,1,NOW()-INTERVAL 170 DAY),
 (3 ,'greenleaf@foodloop.test'  ,@pw,'Meena Subramani'     ,'9000000003',2,'ACTIVE' ,1,NOW()-INTERVAL 160 DAY),
 (4 ,'freshmart@foodloop.test'  ,@pw,'Arun Prakash'        ,'9000000004',2,'ACTIVE' ,1,NOW()-INTERVAL 150 DAY),
 (5 ,'celebration@foodloop.test',@pw,'Lakshmi Narayanan'   ,'9000000005',2,'ACTIVE' ,1,NOW()-INTERVAL 140 DAY),
 (6 ,'hopemeals@foodloop.test'  ,@pw,'Priya Dharshini'     ,'9000000006',3,'ACTIVE' ,1,NOW()-INTERVAL 165 DAY),
 (7 ,'annadhanam@foodloop.test' ,@pw,'Senthil Murugan'     ,'9000000007',3,'ACTIVE' ,1,NOW()-INTERVAL 155 DAY),
 (8 ,'littlehearts@foodloop.test',@pw,'Fathima Beevi'      ,'9000000008',3,'ACTIVE' ,1,NOW()-INTERVAL 145 DAY),
 (9 ,'vol.karthik@foodloop.test',@pw,'Karthik Raja'        ,'9000000009',4,'ACTIVE' ,1,NOW()-INTERVAL 130 DAY),
 (10,'vol.divya@foodloop.test'  ,@pw,'Divya Bharathi'      ,'9000000010',4,'ACTIVE' ,1,NOW()-INTERVAL 125 DAY),
 (11,'vol.sanjay@foodloop.test' ,@pw,'Sanjay Kumar'        ,'9000000011',4,'ACTIVE' ,1,NOW()-INTERVAL 120 DAY),
 (12,'seva@foodloop.test'       ,@pw,'Gowri Shankar'       ,'9000000012',3,'PENDING',0,NOW()-INTERVAL 1 DAY);

-- ---------- Locations ----------
INSERT INTO locations (id,address_line,area,city,state,pincode,latitude,longitude) VALUES
 (1 ,'12 Sample Street','RS Puram'      ,'Coimbatore','Tamil Nadu','641002',11.007400,76.950000),
 (2 ,'45 Sample Road'  ,'Gandhipuram'   ,'Coimbatore','Tamil Nadu','641012',11.018300,76.972500),
 (3 ,'78 Sample Avenue','Peelamedu'     ,'Coimbatore','Tamil Nadu','641004',11.027000,77.020000),
 (4 ,'9 Sample Lane'   ,'Race Course'   ,'Coimbatore','Tamil Nadu','641018',11.001000,76.975000),
 (5 ,'23 Sample Nagar' ,'Singanallur'   ,'Coimbatore','Tamil Nadu','641005',10.999000,77.030000),
 (6 ,'67 Sample Colony','Ukkadam'       ,'Coimbatore','Tamil Nadu','641001',10.990000,76.960000),
 (7 ,'31 Sample Street','Vadavalli'     ,'Coimbatore','Tamil Nadu','641041',11.023000,76.904000),
 (8 ,'5 Sample Road'   ,'Saibaba Colony','Coimbatore','Tamil Nadu','641011',11.025000,76.940000),
 (9 ,'Volunteer base'  ,'Gandhipuram'   ,'Coimbatore','Tamil Nadu','641012',11.017000,76.970000),
 (10,'Volunteer base'  ,'RS Puram'      ,'Coimbatore','Tamil Nadu','641002',11.006000,76.955000),
 (11,'Volunteer base'  ,'Singanallur'   ,'Coimbatore','Tamil Nadu','641005',10.998000,77.028000);

-- ---------- Profiles ----------
INSERT INTO donor_profiles (user_id,organization_name,donor_type,fssai_license_no,location_id,verification_status,verified_by,verified_at) VALUES
 (2,'Annapoorna Residency (sample)'   ,'HOTEL'          ,'10000000000001',1,'VERIFIED',1,NOW()-INTERVAL 169 DAY),
 (3,'Green Leaf Bakery (sample)'      ,'BAKERY'         ,'10000000000002',2,'VERIFIED',1,NOW()-INTERVAL 159 DAY),
 (4,'FreshMart Supermarket (sample)'  ,'SUPERMARKET'    ,'10000000000003',3,'VERIFIED',1,NOW()-INTERVAL 149 DAY),
 (5,'Celebration Hall Events (sample)','EVENT_ORGANIZER','10000000000004',4,'VERIFIED',1,NOW()-INTERVAL 139 DAY);

INSERT INTO ngo_profiles (user_id,organization_name,registration_number,org_type,contact_person,location_id,daily_capacity_kg,available_from,available_to,beneficiaries_served,verification_status,verified_by,verified_at) VALUES
 (6 ,'Hope Meals Trust (sample)'      ,'TN/NGO/SAMPLE/0001','NGO'      ,'Priya Dharshini',5,150.00,'08:00:00','21:00:00',420,'VERIFIED',1,NOW()-INTERVAL 164 DAY),
 (7 ,'Annadhanam Foundation (sample)' ,'TN/NGO/SAMPLE/0002','FOOD_BANK','Senthil Murugan',6,300.00,'06:00:00','22:00:00',900,'VERIFIED',1,NOW()-INTERVAL 154 DAY),
 (8 ,'Little Hearts Charity (sample)' ,'TN/NGO/SAMPLE/0003','CHARITY'  ,'Fathima Beevi'  ,7, 80.00,'09:00:00','20:00:00',150,'VERIFIED',1,NOW()-INTERVAL 144 DAY),
 (12,'Seva Community Kitchen (sample)','TN/NGO/SAMPLE/0004','NGO'      ,'Gowri Shankar'  ,8,100.00,'08:00:00','20:00:00',  0,'PENDING' ,NULL,NULL);

INSERT INTO volunteer_profiles (user_id,vehicle_type,service_radius_km,availability_status,base_location_id,total_deliveries) VALUES
 (9 ,'BIKE'   ,12.0,'BUSY'     ,9 ,2),
 (10,'CAR'    ,20.0,'BUSY'     ,10,2),
 (11,'BICYCLE', 6.0,'AVAILABLE',11,1);

-- ---------- Food catalogue ----------
INSERT INTO food_categories (id,name,description,is_perishable,safe_hours_after_prep) VALUES
 (1,'Cooked Meals'       ,'Rice, curries, biryani, meals boxes'     ,1,  4),
 (2,'Bakery'             ,'Bread, buns, cakes, pastries'            ,1, 48),
 (3,'Fruits & Vegetables','Fresh produce'                           ,1, 72),
 (4,'Dairy'              ,'Milk, curd, paneer, packaged dairy'      ,1, 24),
 (5,'Packaged Food'      ,'Sealed packaged products before expiry'  ,0,720),
 (6,'Beverages'          ,'Juices, packaged drinks'                 ,0,168),
 (7,'Grains & Staples'   ,'Rice, flour, pulses'                     ,0,720),
 (8,'Sweets'             ,'Traditional and packaged sweets'         ,1, 48);

INSERT INTO food_items (id,category_id,name,default_unit,kg_per_unit) VALUES
 (1,1,'Veg Meals'        ,'PORTIONS',0.400),
 (2,1,'Biryani'          ,'PORTIONS',0.450),
 (3,2,'Bread Loaf'       ,'PIECES'  ,0.400),
 (4,2,'Buns'             ,'PIECES'  ,0.080),
 (5,3,'Mixed Vegetables' ,'KG'      ,1.000),
 (6,4,'Packaged Milk'    ,'LITRE'   ,1.030),
 (7,4,'Curd'             ,'KG'      ,1.000),
 (8,8,'Mixed Sweets'     ,'KG'      ,1.000);

-- ---------- Donations (13 rows across statuses and months) ----------
INSERT INTO donations
 (id,donation_code,donor_id,category_id,food_item_id,food_name,quantity,unit,quantity_kg,prepared_at,expires_at,storage_condition,allergens,is_vegetarian,description,pickup_location_id,pickup_window_start,pickup_window_end,safety_declared,status,accepted_ngo_id,created_at) VALUES
 -- completed recently
 (1 ,'FL-2026-000001',2,1,1,'Veg Meals'      ,60,'PORTIONS',24.00,NOW()-INTERVAL 75 HOUR ,NOW()-INTERVAL 71 HOUR ,'HOT_HELD'  ,'None'            ,1,'Wedding lunch surplus'     ,1,NOW()-INTERVAL 74 HOUR,NOW()-INTERVAL 72 HOUR,1,'COMPLETED',6,NOW()-INTERVAL 75 HOUR),
 (2 ,'FL-2026-000002',3,2,3,'Bread Loaf'     ,30,'PIECES'  ,12.00,NOW()-INTERVAL 52 HOUR ,NOW()-INTERVAL 4 HOUR  ,'ROOM_TEMP' ,'Gluten, Milk'    ,1,'End-of-day bread'         ,2,NOW()-INTERVAL 50 HOUR,NOW()-INTERVAL 48 HOUR,1,'COMPLETED',8,NOW()-INTERVAL 52 HOUR),
 -- expired (wasted)
 (3 ,'FL-2026-000003',4,3,5,'Mixed Vegetables',20,'KG'     ,20.00,NOW()-INTERVAL 140 HOUR,NOW()-INTERVAL 120 HOUR,'ROOM_TEMP' ,'None'            ,1,'No NGO accepted in time'  ,3,NOW()-INTERVAL 139 HOUR,NOW()-INTERVAL 125 HOUR,1,'EXPIRED',NULL,NOW()-INTERVAL 140 HOUR),
 -- live: matched, waiting for NGO response
 (4 ,'FL-2026-000004',5,1,2,'Biryani'        ,80,'PORTIONS',36.00,NOW()-INTERVAL 1 HOUR  ,NOW()+INTERVAL 3 HOUR  ,'HOT_HELD'  ,'Nuts (cashew)'   ,0,'Corporate event surplus'  ,4,NOW()+INTERVAL 30 MINUTE,NOW()+INTERVAL 2 HOUR,1,'MATCHED',NULL,NOW()-INTERVAL 30 MINUTE),
 -- live: accepted, waiting for volunteer
 (5 ,'FL-2026-000005',2,1,1,'Veg Meals'      ,50,'PORTIONS',20.00,NOW()-INTERVAL 1 HOUR  ,NOW()+INTERVAL 3 HOUR  ,'HOT_HELD'  ,'None'            ,1,'Lunch buffet surplus'     ,1,NOW()+INTERVAL 20 MINUTE,NOW()+INTERVAL 2 HOUR,1,'ACCEPTED',7,NOW()-INTERVAL 40 MINUTE),
 -- live: in transit
 (6 ,'FL-2026-000006',4,4,6,'Packaged Milk'  ,40,'LITRE'   ,41.20,NOW()-INTERVAL 5 HOUR  ,NOW()+INTERVAL 19 HOUR ,'REFRIGERATED','Milk'          ,1,'Near-date milk packets'   ,3,NOW()-INTERVAL 1 HOUR,NOW()+INTERVAL 1 HOUR,1,'IN_TRANSIT',6,NOW()-INTERVAL 3 HOUR),
 -- cancelled by donor
 (7 ,'FL-2026-000007',3,2,4,'Buns'           ,100,'PIECES' , 8.00,NOW()-INTERVAL 10 HOUR ,NOW()+INTERVAL 30 HOUR ,'ROOM_TEMP' ,'Gluten'          ,1,'Cancelled: sold on counter',2,NOW()-INTERVAL 9 HOUR,NOW()-INTERVAL 6 HOUR,1,'CANCELLED',NULL,NOW()-INTERVAL 10 HOUR),
 -- live: volunteer assigned
 (8 ,'FL-2026-000008',3,2,3,'Bread Loaf'     ,25,'PIECES'  ,10.00,NOW()-INTERVAL 3 HOUR  ,NOW()+INTERVAL 40 HOUR ,'ROOM_TEMP' ,'Gluten, Milk'    ,1,'Evening bread surplus'    ,2,NOW()+INTERVAL 10 MINUTE,NOW()+INTERVAL 3 HOUR,1,'VOLUNTEER_ASSIGNED',8,NOW()-INTERVAL 2 HOUR),
 -- live: just listed, matching not yet run
 (9 ,'FL-2026-000009',2,8,8,'Mixed Sweets'   ,6,'KG'       , 6.00,NOW()-INTERVAL 2 HOUR  ,NOW()+INTERVAL 46 HOUR ,'ROOM_TEMP' ,'Nuts, Milk'      ,1,'Festival sweets box'      ,1,NOW()+INTERVAL 1 HOUR,NOW()+INTERVAL 5 HOUR,1,'AVAILABLE',NULL,NOW()-INTERVAL 5 MINUTE),
 -- history for monthly trends
 (10,'FL-2026-000010',2,1,1,'Veg Meals'      ,70,'PORTIONS',28.00,NOW()-INTERVAL 60 DAY ,NOW()-INTERVAL 60 DAY+INTERVAL 4 HOUR ,'HOT_HELD' ,'None'  ,1,'History',1,NOW()-INTERVAL 60 DAY+INTERVAL 30 MINUTE,NOW()-INTERVAL 60 DAY+INTERVAL 2 HOUR,1,'COMPLETED',7,NOW()-INTERVAL 60 DAY),
 (11,'FL-2026-000011',4,3,5,'Mixed Vegetables',35,'KG'     ,35.00,NOW()-INTERVAL 40 DAY ,NOW()-INTERVAL 40 DAY+INTERVAL 72 HOUR,'ROOM_TEMP','None'  ,1,'History',3,NOW()-INTERVAL 40 DAY+INTERVAL 1 HOUR,NOW()-INTERVAL 40 DAY+INTERVAL 10 HOUR,1,'COMPLETED',6,NOW()-INTERVAL 40 DAY),
 (12,'FL-2026-000012',5,1,2,'Biryani'        ,45,'PORTIONS',20.25,NOW()-INTERVAL 35 DAY ,NOW()-INTERVAL 35 DAY+INTERVAL 4 HOUR ,'HOT_HELD' ,'None'  ,0,'History - expired',4,NOW()-INTERVAL 35 DAY+INTERVAL 30 MINUTE,NOW()-INTERVAL 35 DAY+INTERVAL 2 HOUR,1,'EXPIRED',NULL,NOW()-INTERVAL 35 DAY),
 (13,'FL-2026-000013',3,2,3,'Bread Loaf'     ,40,'PIECES'  ,16.00,NOW()-INTERVAL 95 DAY ,NOW()-INTERVAL 95 DAY+INTERVAL 48 HOUR,'ROOM_TEMP','Gluten','1' ,'History',2,NOW()-INTERVAL 95 DAY+INTERVAL 1 HOUR,NOW()-INTERVAL 95 DAY+INTERVAL 6 HOUR,1,'COMPLETED',8,NOW()-INTERVAL 95 DAY);

INSERT INTO donation_images (donation_id,stored_file_name,original_file_name,content_type,size_bytes) VALUES
 (4,'3f2a9c1e-0000-4000-8000-000000000001.jpg','biryani.jpg','image/jpeg',184320),
 (5,'3f2a9c1e-0000-4000-8000-000000000002.jpg','meals.jpg'  ,'image/jpeg',201728),
 (8,'3f2a9c1e-0000-4000-8000-000000000003.png','bread.png'  ,'image/png' ,150000);

-- ---------- NGO requirements ----------
INSERT INTO ngo_requirements (ngo_id,category_id,required_quantity_kg,needed_by,priority,status,notes) VALUES
 (6,1,40.00,NOW()+INTERVAL 1 DAY ,'HIGH'  ,'OPEN'     ,'Dinner for shelter residents'),
 (7,1,80.00,NOW()+INTERVAL 1 DAY ,'URGENT','OPEN'     ,'Community kitchen tonight'),
 (7,3,50.00,NOW()+INTERVAL 2 DAY ,'MEDIUM','OPEN'     ,'Vegetables for weekly kitchen'),
 (8,2,15.00,NOW()+INTERVAL 1 DAY ,'MEDIUM','OPEN'     ,'Breakfast for children home'),
 (8,4,20.00,NOW()+INTERVAL 1 DAY ,'HIGH'  ,'OPEN'     ,'Milk for children'),
 (6,4,40.00,NOW()-INTERVAL 1 DAY ,'HIGH'  ,'FULFILLED','Fulfilled by FL-2026-000006');

-- ---------- AI matches (sample scores; weights 30/25/15/15/15) ----------
INSERT INTO donation_matches (donation_id,ngo_id,match_score,distance_km,rank_position,score_breakdown,status,responded_at) VALUES
 (4,6,87.40, 9.8,1,'{"distance":26.1,"category":25.0,"quantity":13.5,"capacity":12.8,"expiry":10.0}','NOTIFIED',NULL),
 (4,7,82.10, 6.4,2,'{"distance":22.0,"category":25.0,"quantity":15.0,"capacity":12.1,"expiry":8.0}' ,'NOTIFIED',NULL),
 (4,8,64.80,14.1,3,'{"distance":18.3,"category":10.0,"quantity":14.0,"capacity":13.5,"expiry":9.0}' ,'NOTIFIED',NULL),
 (1,6,90.00, 8.0,1,'{"distance":27.0,"category":25.0,"quantity":15.0,"capacity":13.0,"expiry":10.0}','ACCEPTED',NOW()-INTERVAL 74 HOUR),
 (2,8,85.00, 6.0,1,'{"distance":24.0,"category":25.0,"quantity":14.0,"capacity":12.0,"expiry":10.0}','ACCEPTED',NOW()-INTERVAL 51 HOUR),
 (5,7,88.00, 5.0,1,'{"distance":26.0,"category":25.0,"quantity":15.0,"capacity":12.0,"expiry":10.0}','ACCEPTED',NOW()-INTERVAL 35 MINUTE),
 (5,6,79.00,10.0,2,'{"distance":20.0,"category":25.0,"quantity":13.0,"capacity":12.0,"expiry":9.0}' ,'WITHDRAWN',NOW()-INTERVAL 35 MINUTE),
 (6,6,83.00, 4.0,1,'{"distance":25.0,"category":25.0,"quantity":12.0,"capacity":11.0,"expiry":10.0}','ACCEPTED',NOW()-INTERVAL 150 MINUTE),
 (8,8,86.00, 7.0,1,'{"distance":25.0,"category":25.0,"quantity":14.0,"capacity":12.0,"expiry":10.0}','ACCEPTED',NOW()-INTERVAL 100 MINUTE),
 (10,7,84.00, 6.0,1,'{"distance":24.0,"category":25.0,"quantity":13.0,"capacity":12.0,"expiry":10.0}','ACCEPTED',NOW()-INTERVAL 60 DAY+INTERVAL 20 MINUTE),
 (11,6,80.00,10.0,1,'{"distance":22.0,"category":25.0,"quantity":13.0,"capacity":12.0,"expiry":8.0}' ,'ACCEPTED',NOW()-INTERVAL 40 DAY+INTERVAL 30 MINUTE),
 (13,8,78.00,11.0,1,'{"distance":20.0,"category":25.0,"quantity":12.0,"capacity":11.0,"expiry":10.0}','ACCEPTED',NOW()-INTERVAL 95 DAY+INTERVAL 30 MINUTE);

-- ---------- Pickups & deliveries ----------
INSERT INTO pickup_requests (id,donation_id,ngo_id,volunteer_id,scheduled_pickup_at,status,assigned_at,created_at) VALUES
 (1,1 ,6,9 ,NOW()-INTERVAL 73 HOUR ,'PICKED_UP'         ,NOW()-INTERVAL 74 HOUR,NOW()-INTERVAL 74 HOUR),
 (2,2 ,8,10,NOW()-INTERVAL 49 HOUR ,'PICKED_UP'         ,NOW()-INTERVAL 50 HOUR,NOW()-INTERVAL 51 HOUR),
 (3,5 ,7,NULL,NOW()+INTERVAL 45 MINUTE,'PENDING_ASSIGNMENT',NULL               ,NOW()-INTERVAL 35 MINUTE),
 (4,6 ,6,9 ,NOW()-INTERVAL 30 MINUTE,'PICKED_UP'         ,NOW()-INTERVAL 140 MINUTE,NOW()-INTERVAL 150 MINUTE),
 (5,8 ,8,10,NOW()+INTERVAL 30 MINUTE,'ASSIGNED'          ,NOW()-INTERVAL 90 MINUTE,NOW()-INTERVAL 100 MINUTE),
 (6,10,7,11,NOW()-INTERVAL 60 DAY+INTERVAL 1 HOUR ,'PICKED_UP',NOW()-INTERVAL 60 DAY+INTERVAL 25 MINUTE,NOW()-INTERVAL 60 DAY+INTERVAL 20 MINUTE),
 (7,11,6,9 ,NOW()-INTERVAL 40 DAY+INTERVAL 2 HOUR ,'PICKED_UP',NOW()-INTERVAL 40 DAY+INTERVAL 40 MINUTE,NOW()-INTERVAL 40 DAY+INTERVAL 30 MINUTE),
 (8,13,8,10,NOW()-INTERVAL 95 DAY+INTERVAL 2 HOUR ,'PICKED_UP',NOW()-INTERVAL 95 DAY+INTERVAL 40 MINUTE,NOW()-INTERVAL 95 DAY+INTERVAL 30 MINUTE);

INSERT INTO deliveries (pickup_request_id,status,picked_up_at,delivered_at,receipt_confirmed_at,quantity_received_kg,last_latitude,last_longitude,confirmation_note) VALUES
 (1,'RECEIPT_CONFIRMED',NOW()-INTERVAL 73 HOUR,NOW()-INTERVAL 72 HOUR,NOW()-INTERVAL 71 HOUR,24.00,10.999000,77.030000,'All food received in good condition'),
 (2,'RECEIPT_CONFIRMED',NOW()-INTERVAL 49 HOUR,NOW()-INTERVAL 48 HOUR,NOW()-INTERVAL 47 HOUR,12.00,11.023000,76.904000,'Received, thank you'),
 (4,'IN_TRANSIT'       ,NOW()-INTERVAL 25 MINUTE,NULL,NULL,NULL,11.015000,77.000000,NULL),
 (5,'NOT_STARTED'      ,NULL,NULL,NULL,NULL,NULL,NULL,NULL),
 (6,'RECEIPT_CONFIRMED',NOW()-INTERVAL 60 DAY+INTERVAL 1 HOUR,NOW()-INTERVAL 60 DAY+INTERVAL 2 HOUR,NOW()-INTERVAL 60 DAY+INTERVAL 3 HOUR,28.00,10.990000,76.960000,'Received'),
 (7,'RECEIPT_CONFIRMED',NOW()-INTERVAL 40 DAY+INTERVAL 2 HOUR,NOW()-INTERVAL 40 DAY+INTERVAL 3 HOUR,NOW()-INTERVAL 40 DAY+INTERVAL 4 HOUR,34.50,10.999000,77.030000,'0.5 kg unusable, discarded'),
 (8,'RECEIPT_CONFIRMED',NOW()-INTERVAL 95 DAY+INTERVAL 2 HOUR,NOW()-INTERVAL 95 DAY+INTERVAL 3 HOUR,NOW()-INTERVAL 95 DAY+INTERVAL 4 HOUR,16.00,11.023000,76.904000,'Received');

-- ---------- Notifications ----------
INSERT INTO notifications (user_id,type,title,message,related_entity_type,related_entity_id,is_read,email_status) VALUES
 (6 ,'NEW_MATCH'         ,'New donation match'   ,'FL-2026-000004 Biryani 36 kg - match score 87.4'            ,'DONATION',4,0,'SENT'),
 (7 ,'NEW_MATCH'         ,'New donation match'   ,'FL-2026-000004 Biryani 36 kg - match score 82.1'            ,'DONATION',4,0,'SENT'),
 (8 ,'NEW_MATCH'         ,'New donation match'   ,'FL-2026-000004 Biryani 36 kg - match score 64.8'            ,'DONATION',4,1,'SENT'),
 (2 ,'DONATION_ACCEPTED' ,'Donation accepted'    ,'Annadhanam Foundation accepted FL-2026-000005'             ,'DONATION',5,0,'SENT'),
 (3 ,'VOLUNTEER_ASSIGNED','Volunteer assigned'   ,'Divya Bharathi will collect FL-2026-000008'                ,'DONATION',8,0,'SENT'),
 (10,'VOLUNTEER_ASSIGNED','New pickup task'      ,'Collect FL-2026-000008 from Gandhipuram within 30 minutes'  ,'PICKUP',5,0,'NOT_REQUIRED'),
 (4 ,'DELIVERY_UPDATE'   ,'Food in transit'      ,'FL-2026-000006 has been picked up and is on the way'        ,'DONATION',6,0,'NOT_REQUIRED'),
 (2 ,'DELIVERY_UPDATE'   ,'Donation completed'   ,'FL-2026-000001 was received by Hope Meals Trust'            ,'DONATION',1,1,'SENT'),
 (1 ,'SYSTEM'            ,'NGO awaiting approval','Seva Community Kitchen registered and needs verification'  ,'USER',12,0,'NOT_REQUIRED'),
 (2 ,'EXPIRY_REMINDER'   ,'Expiry reminder'      ,'FL-2026-000009 expires in 46 hours - still available'       ,'DONATION',9,0,'NOT_REQUIRED');

-- ---------- Sample predictions (development placeholders, NOT real model output) ----------
INSERT INTO predictions (prediction_type,donor_id,ngo_id,donation_id,category_id,input_json,output_json,predicted_value,model_name,model_version,trained_on_synthetic,latency_ms) VALUES
 ('SURPLUS',5,NULL,4,1,'{"category":"Cooked Meals","day_of_week":"MON","season":"WINTER","event":true,"previous_quantity_kg":30}','{"predicted_surplus_kg":34.5,"note":"sample"}',34.50,'sample-seed','0.0',1,45),
 ('SURPLUS',2,NULL,5,1,'{"category":"Cooked Meals","day_of_week":"MON","season":"WINTER","event":false,"previous_quantity_kg":22}','{"predicted_surplus_kg":19.0,"note":"sample"}',19.00,'sample-seed','0.0',1,41),
 ('DEMAND',NULL,7,NULL,1,'{"category":"Cooked Meals","area":"Ukkadam","day_of_week":"MON"}','{"predicted_demand_kg":75.0,"note":"sample"}',75.00,'sample-seed','0.0',1,38);

-- ---------- Feedback ----------
INSERT INTO feedback (donation_id,from_user_id,to_user_id,kind,rating,comment,status,admin_response) VALUES
 (1,6,2 ,'RATING'   ,5,'Well packed and on time','RESOLVED',NULL),
 (1,2,6 ,'RATING'   ,5,'Quick response from NGO','RESOLVED',NULL),
 (2,8,3 ,'RATING'   ,4,'Bread was fresh'        ,'RESOLVED',NULL),
 (11,6,4,'COMPLAINT',NULL,'Some vegetables were spoiled','RESOLVED','Donor reminded about quality checks');

-- ---------- Audit logs ----------
INSERT INTO audit_logs (user_id,action,entity_type,entity_id,old_value,new_value,ip_address) VALUES
 (1 ,'VERIFY_NGO'      ,'NGO_PROFILE',6 ,'{"verification_status":"PENDING"}','{"verification_status":"VERIFIED"}','127.0.0.1'),
 (2 ,'CREATE_DONATION' ,'DONATION'   ,5 ,NULL,'{"status":"AVAILABLE"}','127.0.0.1'),
 (7 ,'ACCEPT_DONATION' ,'DONATION'   ,5 ,'{"status":"MATCHED"}','{"status":"ACCEPTED"}','127.0.0.1'),
 (3 ,'CANCEL_DONATION' ,'DONATION'   ,7 ,'{"status":"AVAILABLE"}','{"status":"CANCELLED"}','127.0.0.1'),
 (NULL,'EXPIRE_DONATION','DONATION'  ,3 ,'{"status":"AVAILABLE"}','{"status":"EXPIRED"}',NULL);
