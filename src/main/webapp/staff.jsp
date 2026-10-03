<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.*" %>
<% request.setAttribute("pageTitle","Staff Directory"); %>
<%@ include file="WEB-INF/header.jsp" %>
<%
 Map<String,Object> edit=(Map<String,Object>)request.getAttribute("editStaff");
 Set<Integer> editSkills=(Set<Integer>)request.getAttribute("editSkills");
 boolean editing=edit!=null;
%>
<div class="cardx pad">
  <div class="toolbar">
    <div>
      <b>Ground staff</b>
      <div class="mini"><%= "ADMIN".equals(current.role()) ? "Create, edit, manage skills, manage login access and activate/deactivate ground staff." : "View ground staff assigned to your department(s). Staff management is administrator-only." %></div>
    </div>
    <div class="d-flex gap-2 align-items-center">
      <input id="staffSearch" onkeyup="filterTable('staffSearch','staffTable')" class="form-control" style="width:230px" placeholder="Search staff">
      <% if("ADMIN".equals(current.role())) { %>
        <button class="btn-main" type="button" onclick="openAddStaff()"><i class="fa-solid fa-user-plus me-1"></i>Add staff</button>
      <% } %>
    </div>
  </div>

  <% if(!"ADMIN".equals(current.role())) { %>
    <div class="info-strip mb-3"><i class="fa-solid fa-shield-halved"></i><div><b>Read-only directory</b><div class="mini">Your view is limited to staff in your assigned department(s). Only an administrator can create, edit, deactivate or reactivate staff.</div></div></div>
  <% } %>

  <div class="table-responsive">
    <table class="table" id="staffTable">
      <thead><tr><th>Staff</th><th>Work area</th><th>Role</th><th>Experience</th><th>Contact</th><th>Login</th><th>Status</th><th>Manage</th></tr></thead>
      <tbody>
      <% List<Map<String,Object>> rows=(List<Map<String,Object>>)request.getAttribute("rows");
         for(Map<String,Object> x:rows){
           boolean active="ACTIVE".equals(String.valueOf(x.get("c7")));
           boolean hasLogin=x.get("c10")!=null;
      %>
        <tr>
          <td><b><%=x.get("c3")%></b><div class="mini"><%=x.get("c2")%></div></td>
          <td><%=x.get("c8")%></td>
          <td><%=x.get("c9")%></td>
          <td><%=x.get("c6")%> yrs</td>
          <td><%=x.get("c4")==null?"":x.get("c4")%><br><span class="mini"><%=x.get("c5")==null?"":x.get("c5")%></span></td>
          <td><span class="badge-soft <%=hasLogin?"b-blue":"b-gray"%>"><%=hasLogin?"ENABLED":"NO LOGIN"%></span></td>
          <td><span class="badge-soft <%=active?"b-green":"b-gray"%>"><%=x.get("c7")%></span></td>
          <td>
          <% if("ADMIN".equals(current.role())) { %>
            <div class="d-flex gap-1 flex-wrap">
              <a class="btn-soft" href="staff?edit=<%=x.get("c1")%>"><i class="fa-solid fa-pen-to-square me-1"></i>Manage</a>
              <% if(active){ %>
                <form method="post" action="staff" onsubmit="return confirmAction('Deactivate this staff member? Their login will also be disabled.')"><input type="hidden" name="action" value="deactivate"><input type="hidden" name="staff_id" value="<%=x.get("c1")%>"><button class="btn-soft" type="submit">Deactivate</button></form>
              <% } else { %>
                <form method="post" action="staff"><input type="hidden" name="action" value="reactivate"><input type="hidden" name="staff_id" value="<%=x.get("c1")%>"><button class="btn-soft" type="submit">Reactivate</button></form>
              <% } %>
            </div>
          <% } else { %><span class="mini">View only</span><% } %>
          </td>
        </tr>
      <% } %>
      </tbody>
    </table>
  </div>
</div>

<% if("ADMIN".equals(current.role())) { %>
<div class="modal fade" id="staffModal" tabindex="-1">
  <div class="modal-dialog modal-xl"><div class="modal-content">
    <form method="post" action="staff" id="staffForm">
      <div class="modal-header">
        <div><b id="staffModalTitle">Add staff member</b><div class="mini">Manage the staff profile, verified skills and optional login account used by Smart Allocation.</div></div>
        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
      </div>
      <div class="modal-body">
        <input type="hidden" name="action" value="save">
        <input type="hidden" name="staff_id" id="staff_id" value="">
        <div class="row g-3">
          <div class="col-md-4"><label class="form-label">Staff code</label><input class="form-control" name="staff_code" id="staff_code" required placeholder="STF-105"></div>
          <div class="col-md-4"><label class="form-label">Full name</label><input class="form-control" name="name" id="staff_name" required></div>
          <div class="col-md-4"><label class="form-label">Experience (years)</label><input class="form-control" type="number" min="0" max="60" step="0.5" name="experience_years" id="staff_experience" value="0"></div>
          <div class="col-md-4"><label class="form-label">Phone</label><input class="form-control" name="phone" id="staff_phone" pattern="[0-9]{10}"></div>
          <div class="col-md-4"><label class="form-label">Email</label><input class="form-control" name="email" id="staff_email" type="email"></div>
          <div class="col-md-4"><label class="form-label">Work area</label><select class="form-select" name="department_id" id="staff_department" required><%for(Map<String,Object>x:(List<Map<String,Object>>)request.getAttribute("departments")){%><option value="<%=x.get("id")%>"><%=x.get("name")%></option><%}%></select></div>
          <div class="col-md-6"><label class="form-label">Role</label><select class="form-select" name="role_id" id="staff_role" required><%for(Map<String,Object>x:(List<Map<String,Object>>)request.getAttribute("roles")){%><option value="<%=x.get("id")%>"><%=x.get("name")%></option><%}%></select></div>
          <div class="col-md-6"><label class="form-label">Login username</label><input class="form-control" name="login_username" id="login_username" placeholder="Optional for staff who need portal access"></div>
          <div class="col-md-6"><label class="form-label">Login password</label><input class="form-control" type="password" name="login_password" id="login_password" placeholder="8+ characters; blank keeps existing password when editing"></div>
          <div class="col-12">
            <div class="info-strip"><i class="fa-solid fa-bullseye"></i><div><b>Verified skills are required for Smart Allocation</b><div class="mini">A staff member is eligible only when every skill required by the duty is present here and marked verified.</div></div></div>
          </div>
          <div class="col-12"><label class="form-label">Verified skills</label><div class="row g-2"><%for(Map<String,Object>x:(List<Map<String,Object>>)request.getAttribute("skills")){int sid=((Number)x.get("id")).intValue();%><div class="col-md-4 col-lg-3"><label class="mini"><input class="staff-skill" type="checkbox" name="skill_id" value="<%=sid%>" data-skill-id="<%=sid%>"> <%=x.get("name")%></label></div><%}%></div></div>
        </div>
      </div>
      <div class="modal-footer"><button type="button" class="btn-soft" data-bs-dismiss="modal">Cancel</button><button class="btn-main" type="submit" id="staffSaveBtn">Save staff</button></div>
    </form>
  </div></div>
</div>
<script>
function clearStaffForm(){
  document.getElementById('staffModalTitle').innerText='Add staff member';
  document.getElementById('staffSaveBtn').innerText='Save staff';
  document.getElementById('staff_id').value='';
  document.getElementById('staff_code').value=''; document.getElementById('staff_name').value=''; document.getElementById('staff_experience').value='0';
  document.getElementById('staff_phone').value=''; document.getElementById('staff_email').value=''; document.getElementById('login_username').value=''; document.getElementById('login_password').value='';
  document.querySelectorAll('.staff-skill').forEach(x=>x.checked=false);
}
function openAddStaff(){clearStaffForm();new bootstrap.Modal(document.getElementById('staffModal')).show();}
<% if(editing){ %>
function openEditStaff(){
  document.getElementById('staffModalTitle').innerText='Manage staff member';
  document.getElementById('staffSaveBtn').innerText='Save changes';
  document.getElementById('staff_id').value='<%=edit.get("id")%>';
  document.getElementById('staff_code').value='<%=String.valueOf(edit.get("code")).replace("'","\\'")%>';
  document.getElementById('staff_name').value='<%=String.valueOf(edit.get("name")).replace("'","\\'")%>';
  document.getElementById('staff_experience').value='<%=edit.get("experience")%>';
  document.getElementById('staff_phone').value='<%=edit.get("phone")==null?"":String.valueOf(edit.get("phone")).replace("'","\\'")%>';
  document.getElementById('staff_email').value='<%=edit.get("email")==null?"":String.valueOf(edit.get("email")).replace("'","\\'")%>';
  document.getElementById('staff_department').value='<%=edit.get("departmentId")%>';
  document.getElementById('staff_role').value='<%=edit.get("roleId")%>';
  document.getElementById('login_username').value='<%=edit.get("username")==null?"":String.valueOf(edit.get("username")).replace("'","\\'")%>';
  document.getElementById('login_password').value='';
  const selected=[<% if(editSkills!=null){boolean first=true;for(Integer s:editSkills){if(!first)out.print(",");first=false;out.print(s);} } %>];
  document.querySelectorAll('.staff-skill').forEach(x=>x.checked=selected.includes(Number(x.dataset.skillId)));
  new bootstrap.Modal(document.getElementById('staffModal')).show();
}
window.addEventListener('DOMContentLoaded',openEditStaff);
<% } %>
</script>
<% } %>
<%@ include file="WEB-INF/footer.jsp" %>
