import type { Metadata } from "next";
export const metadata:Metadata={title:"Note Note",description:"记录、待办与 AI 思考",icons:{icon:"/favicon.svg"}};
export default function RootLayout({children}:{children:React.ReactNode}){return <html lang="zh-CN"><body>{children}</body></html>}
