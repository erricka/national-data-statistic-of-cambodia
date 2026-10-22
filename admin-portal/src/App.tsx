import { Routes, Route } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import { ProtectedRoute } from './components/ProtectedRoute'
import { ScrollToTop } from './common/ScrollToTop'

import DashboardPage from './pages/admin/dashboard/DashboardPage'
import HomePage from './pages/client/home/HomePage'
import UserManagementPage from './pages/admin/user-management/UserManagementPage'
import DataManagementPage from './pages/admin/data-management/DataManagementPage'
import RoleManagementPage from './pages/admin/role-management/RoleManagementPage'
import CategoryManagementPage from './pages/admin/category-management/CategoryManagementPage'
import AddUserPage from './pages/admin/user-management/AddUserPage'
import EditUserPage from './pages/admin/user-management/EditUserPage'
import RoleDetailPage from './pages/admin/role-management/RoleDetailPage'
import CreateRolePage from './pages/admin/role-management/CreateRolePage'
import PermissionManagementPage from './pages/admin/permission-management/PermissionManagementPage'
import CreatePermissionPage from './pages/admin/permission-management/CreatePermissionPage'
import PermissionDetailPage from './pages/admin/permission-management/PermissionDetailPage'
import EditPermissionPage from './pages/admin/permission-management/EditPermissionPage'
import EditRolePage from './pages/admin/role-management/EditRolePage'
import AddDataPage from './pages/admin/data-management/AddDataPage'
import DataDetailPage from './pages/client/information/DataDetailPage'
import AboutPage from './pages/client/about/AboutPage'
import CategoriesPage from './pages/client/categories/CategoriesPage'
import CategoryDetailsPage from './pages/client/categories/CategoryDetailsPage'
import DataExplorePage from './pages/client/data-explore/DataExplorePage'
import CreateTemplatePage from './pages/admin/template-management/CreateTemplatePage'
import TemplateManagementPage from './pages/admin/template-management/TemplateManagementPage'
import ViewTemplatePage from './pages/admin/template-management/ViewTemplatePage'
import EditTemplatePage from './pages/admin/template-management/EditTemplatePage'
import ViewDataDetailPage from './pages/admin/data-management/ViewDataDetailPage'
import LoginPage from './pages/authentication/login/LoginPage'
import ApprovalManagementPage from './pages/admin/approval-management/ApprovalManagementPage'
import ApprovalReviewPage from './pages/admin/approval-management/ApprovalReviewPage'
import ChangePasswordPage from './pages/admin/account/ChangePasswordPage'
import DataDetailReviewPage from './pages/admin/data-management/DataDetailReviewPage'

function App() {
  return (
    <AuthProvider>
      <ScrollToTop />
      <Routes>
        {/* Public Client Routes */}
        <Route path="/" element={<HomePage />} />
        <Route path="/detail/:sourceId" element={<DataDetailPage />} />
        <Route path="/about" element={<AboutPage />} />
        <Route path="/categories" element={<CategoriesPage />} />
        <Route path="/categories/detail" element={<CategoryDetailsPage />} />
        <Route path="/data-explore" element={<DataExplorePage />} />
        <Route path="/login" element={<LoginPage />} />

        {/* Protected Admin Routes */}
        <Route element={<ProtectedRoute />}>
          <Route path="/admin/dashboard" element={<DashboardPage />} />
          <Route path="/admin/user-management" element={<UserManagementPage />} />
          <Route path="/admin/add-user" element={<AddUserPage />} />
          <Route path="/admin/edit-user" element={<EditUserPage />} />
          <Route path="/admin/category-management" element={<CategoryManagementPage />} />
          <Route path="/admin/data-management" element={<DataManagementPage />} />
          <Route path="/admin/data-detail" element={<ViewDataDetailPage />} />
          <Route path="/admin/add-data" element={<AddDataPage />} />
          <Route path="/admin/role-management" element={<RoleManagementPage />} />
          <Route path="/admin/role-detail" element={<RoleDetailPage />} />
          <Route path="/admin/create-role" element={<CreateRolePage />} />
          <Route path="/admin/edit-role" element={<EditRolePage />} />
          <Route path="/admin/permission-management" element={<PermissionManagementPage />} />
          <Route path="/admin/permission-detail" element={<PermissionDetailPage />} />
          <Route path="/admin/create-permission" element={<CreatePermissionPage />} />
          <Route path="/admin/edit-permission" element={<EditPermissionPage />} />
          <Route path="/admin/template-management" element={<TemplateManagementPage />} />
          <Route path="/admin/template-detail" element={<ViewTemplatePage />} />
          <Route path="/admin/create-template" element={<CreateTemplatePage />} />
          <Route path="/admin/edit-template" element={<EditTemplatePage />} />
          <Route path="/admin/approvals" element={<ApprovalManagementPage />} />
          <Route path="/admin/approvals/:uploadId" element={<ApprovalReviewPage />} />
          <Route path="/admin/change-password" element={<ChangePasswordPage />} />
          <Route path="/admin/data-management/:uploadId" element={<DataDetailReviewPage />} />
        </Route>
      </Routes>
    </AuthProvider>
  )
}

export default App