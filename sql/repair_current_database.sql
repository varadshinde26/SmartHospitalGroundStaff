-- NON-DESTRUCTIVE repair for an existing smart_hospital database.
-- Use this if you do NOT want to drop existing tables/data.
-- It ensures the four demo logins, ICU supervisor mapping, Ramesh Patil,
-- Ramesh's verified skills/availability, and the ICU demonstration duty exist.
USE smart_hospital;

-- 1) Demo login accounts. Existing passwords are reset to the project demo passwords.
INSERT INTO users(user_id,username,password_hash,role,status,full_name,email)
VALUES
(1,'admin','PBKDF2$120000$Fama6LkWB2mjcYPBXVwK2A==$Gebpb6tIrATbSyixYO5M4vl5KDYWz4xcpDokwUnj+wc=','ADMIN','ACTIVE','System Administrator','admin@hospital.local'),
(2,'supervisor','PBKDF2$120000$3zKN+MJK7eSpOlyXBMWAQw==$ivUzj/nNQpEmZCsL4yTMkPuFIBWCdKZGnyyH+jis5GQ=','SUPERVISOR','ACTIVE','ICU Supervisor','supervisor@hospital.local'),
(3,'staff101','PBKDF2$120000$3mvcNUsmsbGYeUrW2ynUtg==$GW5CCwRyDzaGkG2vnOtF5dZKUtmvMZ9KRMT+KGWtlZE=','STAFF','ACTIVE','Rahul Patil','rahul@example.com'),
(4,'ramesh101','PBKDF2$120000$INg11062X3ew2RffCXNylw==$xisSoMUFj9/p8QKfsAJp6QgZG35EUj3mmwZ7cBQSD24=','STAFF','ACTIVE','Ramesh Patil','ramesh@example.com')
ON DUPLICATE KEY UPDATE
password_hash=VALUES(password_hash),role=VALUES(role),status='ACTIVE',full_name=VALUES(full_name),email=VALUES(email);

INSERT IGNORE INTO supervisor_departments(supervisor_user_id,department_id,active)
SELECT u.user_id,d.department_id,1 FROM users u JOIN departments d
WHERE u.username='supervisor' AND d.department_name='ICU';

-- 2) Ramesh staff profile.
INSERT INTO staff(user_id,staff_code,name,phone,email,department_id,role_id,experience_years,status)
SELECT u.user_id,'STF-104','Ramesh Patil','9876543213','ramesh@example.com',d.department_id,r.role_id,4.0,'ACTIVE'
FROM users u JOIN departments d JOIN roles r
WHERE u.username='ramesh101' AND d.department_name='ICU' AND r.role_name='ICU Attendant'
  AND NOT EXISTS (SELECT 1 FROM staff WHERE staff_code='STF-104');

UPDATE staff s JOIN users u JOIN departments d JOIN roles r
SET s.user_id=u.user_id,s.name='Ramesh Patil',s.phone='9876543213',s.email='ramesh@example.com',
    s.department_id=d.department_id,s.role_id=r.role_id,s.experience_years=4.0,s.status='ACTIVE'
WHERE s.staff_code='STF-104' AND u.username='ramesh101' AND d.department_name='ICU' AND r.role_name='ICU Attendant';

SET @ramesh_id=(SELECT staff_id FROM staff WHERE staff_code='STF-104' LIMIT 1);
SET @patient_skill=(SELECT skill_id FROM skills WHERE skill_name='Patient Handling' LIMIT 1);
SET @first_aid=(SELECT skill_id FROM skills WHERE skill_name='Basic First Aid' LIMIT 1);
SET @icu_equipment=(SELECT skill_id FROM skills WHERE skill_name='ICU Equipment Assistance' LIMIT 1);
SET @infection=(SELECT skill_id FROM skills WHERE skill_name='Infection Control' LIMIT 1);

INSERT IGNORE INTO staff_skills(staff_id,skill_id,verification_status) VALUES
(@ramesh_id,@patient_skill,'VERIFIED'),(@ramesh_id,@first_aid,'VERIFIED'),
(@ramesh_id,@icu_equipment,'VERIFIED'),(@ramesh_id,@infection,'VERIFIED');

-- 3) Make Ramesh available for the next 31 days, including 2026-10-04.
INSERT INTO staff_availability(staff_id,available_date,start_time,end_time,status)
SELECT @ramesh_id,DATE_ADD(CURDATE(),INTERVAL n DAY),'00:00:00','23:59:59','AVAILABLE'
FROM (
 SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14
 UNION ALL SELECT 15 UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19
 UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24
 UNION ALL SELECT 25 UNION ALL SELECT 26 UNION ALL SELECT 27 UNION ALL SELECT 28 UNION ALL SELECT 29 UNION ALL SELECT 30
) days
WHERE NOT EXISTS (
 SELECT 1 FROM staff_availability sa WHERE sa.staff_id=@ramesh_id
 AND sa.available_date=DATE_ADD(CURDATE(),INTERVAL n DAY)
);

-- 4) Repair the demonstration duty.
SET @icu=(SELECT department_id FROM departments WHERE department_name='ICU' LIMIT 1);
SET @icu_role=(SELECT role_id FROM roles WHERE role_name='ICU Attendant' LIMIT 1);
SET @duty=(SELECT duty_id FROM duties WHERE duty_name='ICU Ground Support Demonstration' ORDER BY duty_id LIMIT 1);

UPDATE duties SET department_id=@icu,role_id=@icu_role,duty_date=DATE_ADD(CURDATE(),INTERVAL 1 DAY),
start_time='09:00:00',end_time='13:00:00',priority='HIGH'
WHERE duty_id=@duty AND status='OPEN';

INSERT IGNORE INTO duty_required_skills(duty_id,skill_id)
SELECT @duty,@patient_skill WHERE @duty IS NOT NULL AND @patient_skill IS NOT NULL;

SELECT user_id,username,role,status,full_name FROM users ORDER BY user_id;
SELECT staff_id,staff_code,name,department_id,role_id,status FROM staff WHERE staff_code='STF-104';
SELECT duty_id,duty_name,duty_date,start_time,end_time,department_id,role_id,priority,status
FROM duties WHERE duty_name='ICU Ground Support Demonstration';
