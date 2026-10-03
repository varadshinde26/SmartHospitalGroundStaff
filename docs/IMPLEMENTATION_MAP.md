# Smart Hospital Ground Staff — Role & Access Map

## Roles

### ADMIN
- Full hospital-wide access.
- Dashboard: hospital-wide workforce, supervisors, duties, proof review and leave overview.
- Staff Directory: add/update/deactivate ground staff across any work area.
- Supervisor Management: create/edit/activate/deactivate/reset-password and assign one or more departments.
- Duty Control: create duties for any department and open explainable allocation.
- Leave: approve/reject any staff leave.
- Attendance: view/record attendance for any active staff member.
- Work Proof: **review only**. No upload control. Can download submitted staff proof and mark work done/reject it.
- Reports: hospital-wide.

### SUPERVISOR
- Must be mapped by ADMIN to one or more departments through `supervisor_departments`.
- Dashboard is limited to mapped departments.
- Staff Directory: **view only** and only mapped departments. No Add Staff button and POST mutations are blocked server-side.
- Duty Control: create/view duties only in mapped departments.
- Smart Allocation: can allocate only duties/staff within mapped departments; hard eligibility is rechecked on assignment.
- Leave: approve/reject only staff in mapped departments.
- Attendance: view/record only staff in mapped departments.
- Work Proof: **review only**. No upload control. Can verify/reject proof only for mapped departments.
- Reports: only mapped departments.
- Cannot open Supervisor Management.

### STAFF
- Dashboard: own assignments, in-progress work, proof status, completed duties and notifications.
- My Duties: acknowledge → start work → submit proof after work.
- Work Proof: upload only against an assigned duty; JPG/JPEG/PNG/PDF, max 5 MB.
- Leave: request and track own leave.
- Attendance: own check-in/check-out.
- Notifications: own notifications.
- Cannot access Staff Directory, Duty Control, Allocation, Supervisor Management or Reports.

## Work completion flow
1. Manager creates a duty.
2. Manager uses Smart Allocation to see eligible candidates.
3. Assignment is made only after department/role/skill/leave/availability/overlap checks pass.
4. Staff acknowledges the assignment.
5. Staff starts work.
6. Staff uploads work proof for that exact assigned duty.
7. Admin/Supervisor checks the submitted file.
8. **Work done / Verify** marks the proof VERIFIED, assignment COMPLETED and duty COMPLETED when no non-completed assignment remains.
9. Reject leaves the work open for correction/re-submission.

## Supervisor mapping
`supervisor_departments` is a many-to-many mapping so one supervisor may supervise multiple work areas, while every supervisor action remains department-scoped.

## Important security rule
UI hiding is not the authorization mechanism. The Java servlets enforce the same rules server-side, so manually posting a request cannot bypass the role/department restrictions.
