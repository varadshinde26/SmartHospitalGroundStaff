# Exact end-to-end verification scenario

## Goal
Verify that Smart Allocation can recommend Ramesh Patil for the ICU Ground Support Demonstration duty and that the assignment reaches the staff dashboard.

## Expected data
- Ramesh Patil: STF-104
- Department: ICU
- Role: ICU Attendant
- Experience: 4 years
- Login: ramesh101 / ramesh@123
- Verified skill: Patient Handling
- Demo duty: ICU Ground Support Demonstration
- Duty date: tomorrow relative to application startup
- Window: 09:00-13:00
- Priority: HIGH
- Required skill: Patient Handling

## Test sequence
1. Log in as `admin / admin@123`.
2. Open Staff Directory.
3. Confirm Ramesh Patil is ACTIVE, ICU, ICU Attendant, and has Manage action.
4. Click Manage. Confirm profile, skills and login fields are editable.
5. Open Duty Control.
6. Find ICU Ground Support Demonstration.
7. Click Recommend.
8. Ramesh must appear as an eligible candidate if he has no conflicting duty/approved leave.
9. Confirm the recommendation shows skill, workload, experience, fairness and suitability score.
10. Click Assign this staff member.
11. Confirm duty status becomes ASSIGNED and Ramesh receives an in-app notification.
12. Log out.
13. Log in as `ramesh101 / ramesh@123`.
14. Dashboard must show Upcoming assignments = 1 and the Next assigned duty card.
15. Open My Duties. The ICU duty must be listed.
16. Acknowledge the duty, then Start Work.
17. Upload work proof from Work Proof.
18. Log in as ICU supervisor.
19. Open Work Proof and verify the submitted proof. Supervisor must not see an upload control.

## If Recommend shows an error
The new AllocationServlet returns the actual server-side error as JSON instead of silently returning an empty list. Open the browser developer console/network response for `/allocation?duty_id=...` and inspect the error.

## Database checks
```sql
USE smart_hospital;
SELECT user_id,username,role,status,full_name FROM users;
SELECT s.staff_code,s.name,d.department_name,r.role_name,s.experience_years,s.status
FROM staff s JOIN departments d ON s.department_id=d.department_id JOIN roles r ON s.role_id=r.role_id
WHERE s.staff_code='STF-104';
SELECT sk.skill_name,ss.verification_status
FROM staff_skills ss JOIN skills sk ON sk.skill_id=ss.skill_id
JOIN staff s ON s.staff_id=ss.staff_id WHERE s.staff_code='STF-104';
SELECT d.duty_id,d.duty_name,d.duty_date,d.start_time,d.end_time,d.priority,d.status
FROM duties d WHERE d.duty_name='ICU Ground Support Demonstration';
SELECT d.duty_id,sk.skill_name
FROM duty_required_skills dr JOIN duties d ON d.duty_id=dr.duty_id JOIN skills sk ON sk.skill_id=dr.skill_id
WHERE d.duty_name='ICU Ground Support Demonstration';
```
