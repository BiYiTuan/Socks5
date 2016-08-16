// ms.h : main header file for the MS application
//

#if !defined(AFX_MS_H__CA1EFE0B_5379_4966_890F_630D2584D81D__INCLUDED_)
#define AFX_MS_H__CA1EFE0B_5379_4966_890F_630D2584D81D__INCLUDED_

#if _MSC_VER > 1000
#pragma once
#endif // _MSC_VER > 1000

#ifndef __AFXWIN_H__
	#error include 'stdafx.h' before including this file for PCH
#endif

#include "resource.h"		// main symbols

/////////////////////////////////////////////////////////////////////////////
// CMsApp:
// See ms.cpp for the implementation of this class
//

class CMsApp : public CWinApp
{
public:
	CMsApp();

// Overrides
	// ClassWizard generated virtual function overrides
	//{{AFX_VIRTUAL(CMsApp)
	public:
	virtual BOOL InitInstance();
	//}}AFX_VIRTUAL

// Implementation

	//{{AFX_MSG(CMsApp)
		// NOTE - the ClassWizard will add and remove member functions here.
		//    DO NOT EDIT what you see in these blocks of generated code !
	//}}AFX_MSG
	DECLARE_MESSAGE_MAP()
};


/////////////////////////////////////////////////////////////////////////////

//{{AFX_INSERT_LOCATION}}
// Microsoft Visual C++ will insert additional declarations immediately before the previous line.

#endif // !defined(AFX_MS_H__CA1EFE0B_5379_4966_890F_630D2584D81D__INCLUDED_)
